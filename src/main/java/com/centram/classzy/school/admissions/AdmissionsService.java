package com.centram.classzy.school.admissions;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.admissions.AdmDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admissions for one school: applications, enquiries, waiting list, offers and admitted students.
 * Every query is limited to CurrentUser.institutionId(). Offers and parent messages are records only: nothing is emailed.
 */
@Service
@Transactional
public class AdmissionsService {

    static final List<Code> STAGES = List.of(new Code("DRAFT", "Draft"), new Code("SUBMITTED", "Submitted"), new Code("UNDER_REVIEW", "In Review"), new Code("DOCUMENTS_PENDING", "Documents Pending"),
            new Code("ASSESSMENT", "Assessment"), new Code("INTERVIEW", "Interview"), new Code("WAITLISTED", "Waitlisted"), new Code("OFFER_ISSUED", "Offer Issued"), new Code("ADMITTED", "Admitted"),
            new Code("REJECTED", "Rejected"), new Code("WITHDRAWN", "Withdrawn"));
    static final Set<String> MANUAL_STAGES = Set.of("SUBMITTED", "UNDER_REVIEW", "DOCUMENTS_PENDING", "ASSESSMENT", "INTERVIEW", "WAITLISTED", "REJECTED", "WITHDRAWN");
    static final Set<String> OFFERABLE = Set.of("SUBMITTED", "UNDER_REVIEW", "DOCUMENTS_PENDING", "ASSESSMENT", "INTERVIEW", "WAITLISTED");
    static final List<Code> SOURCES = List.of(new Code("WALK_IN", "Walk-in"), new Code("PHONE", "Phone"), new Code("WEBSITE", "Website"), new Code("REFERRAL", "Referral"), new Code("SOCIAL", "Social media"), new Code("OTHER", "Other"));
    static final List<Code> DOC_TYPES = List.of(new Code("BIRTH", "Birth certificate"), new Code("REPORT_CARD", "Previous report card"), new Code("TRANSFER", "Transfer certificate"), new Code("ID_PROOF", "ID proof"), new Code("PHOTO", "Photograph"), new Code("OTHER", "Other"));
    static final Set<String> ENQ_STATUS = Set.of("NEW", "FOLLOW_UP", "CONVERTED", "CLOSED");
    static final long MAX_DOC = 5L * 1024 * 1024;
    static final int MAX_DOCS = 10;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    private final com.centram.classzy.school.comm.NotifyService notify;

    public AdmissionsService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, com.centram.classzy.school.comm.NotifyService notify) {
        this.notify = notify;
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ helpers

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }
    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }
    private static String day(Date d) { return d == null ? null : d.toLocalDate().toString(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static String clip(String v, int max) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t); }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }
    private static String like(String q) { return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
    private static boolean has(List<Code> l, String code) { return l.stream().anyMatch(c -> c.code().equals(code)); }
    private static LocalDate date(String v, String key, Map<String, String> err) {
        if (v == null || v.isBlank()) return null;
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { err.put(key, "Use a date like 2026-10-12."); return null; }
    }
    private static String phone(String v, Map<String, String> err, boolean required) {
        String t = clip(v, 30);
        if (t == null) { if (required) err.put("phone", "Required."); return null; }
        String digits = t.replaceAll("[ ()-]", "");
        if (!digits.matches("\\+?[0-9]{7,15}")) { err.put("phone", "Enter a valid mobile number."); return t; }
        return t;
    }
    private static String email(String v, String key, Map<String, String> err) {
        String t = clip(v, 200);
        if (t != null && !t.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) err.put(key, "Enter a valid email address.");
        return t;
    }
    /** Admission and application numbers are handed out one at a time per school. */
    private void lockNumbers() { jdbc.queryForObject("select pg_advisory_xact_lock(hashtext('adm-' || ?))::text", String.class, String.valueOf(inst())); }

    // ------------------------------------------------------------------ options & counts

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        List<Year> years = jdbc.query("select id, name, is_current from academic_years where institution_id = ? and active order by start_date desc", (rs, n) -> new Year(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), i);
        List<ClassOpt> classes = jdbc.query("select id, name, academic_year_id from school_classes where institution_id = ? and active order by coalesce(grade_level, -1), name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getString(2), rs.getLong(3)), i);
        List<SectionOpt> sections = jdbc.query("select s.id, s.school_class_id, s.name, s.max_students, (select count(*) from students st where st.section_id = s.id and st.active) from sections s where s.institution_id = ? and s.active order by s.name",
                (rs, n) -> new SectionOpt(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getObject(4) == null ? null : rs.getInt(4), rs.getLong(5)), i);
        return new Options(years, classes, sections, STAGES, SOURCES, DOC_TYPES);
    }

    public Counts counts() {
        long i = inst();
        expireOffers();
        return new Counts(count("select count(*) from school_applications where institution_id = ? and stage <> 'DRAFT'", i),
                count("select count(*) from school_enquiries where institution_id = ? and status in ('NEW','FOLLOW_UP')", i),
                count("select count(*) from school_applications where institution_id = ? and stage = 'WAITLISTED'", i),
                count("select count(*) from school_offers o join school_applications a on a.id = o.application_id where o.institution_id = ? and o.status in ('PENDING','SENT','ACCEPTED') and a.stage = 'OFFER_ISSUED'", i),
                count("select count(*) from school_applications where institution_id = ? and stage = 'ADMITTED'", i));
    }

    // ------------------------------------------------------------------ applications

    private static final String APP_COLS = "a.id, a.application_no, a.student_name, c.name, a.class_id, a.academic_year_id, a.stage, a.submitted_at, a.guardian_name, a.phone,"
            + " (select count(*) from school_application_documents d where d.application_id = a.id), (select count(*) from school_application_documents d where d.application_id = a.id and d.verified)";
    private static final String APP_FROM = " from school_applications a left join school_classes c on c.id = a.class_id";

    private static AppRow appRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AppRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getObject(6) == null ? null : rs.getLong(6), rs.getString(7), iso(rs.getTimestamp(8)), rs.getString(9), rs.getString(10), rs.getLong(11), rs.getLong(12));
    }

    @Transactional(readOnly = true)
    public AppPage applications(Long yearId, Long classId, String stage, String q, int page, int size) {
        List<String> w = new ArrayList<>(List.of("a.institution_id = ?"));
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (yearId != null) { w.add("a.academic_year_id = ?"); a.add(yearId); }
        if (classId != null) { w.add("a.class_id = ?"); a.add(classId); }
        if (stage != null && !stage.isBlank()) { if (!has(STAGES, stage)) throw ApiException.badRequest("Unknown status."); w.add("a.stage = ?"); a.add(stage); }
        if (q != null && !q.isBlank()) { w.add("(a.student_name ilike ? or a.application_no ilike ? or coalesce(a.guardian_name,'') ilike ?)"); a.add(like(q)); a.add(like(q)); a.add(like(q)); }
        String where = " where " + String.join(" and ", w);
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        long total = count("select count(*)" + APP_FROM + where, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        List<AppRow> items = jdbc.query("select " + APP_COLS + APP_FROM + where + " order by a.submitted_at desc, a.id desc limit ? offset ?", (rs, n) -> appRow(rs), a.toArray());
        return new AppPage(items, total, pg, sz);
    }

    @Transactional(readOnly = true)
    public AppDetail application(Long id) {
        AppRow row = jdbc.query("select " + APP_COLS + APP_FROM + " where a.id = ? and a.institution_id = ?", (rs, n) -> appRow(rs), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Application"));
        Object[] x = jdbc.query("select dob, guardian_email, waitlisted_at, student_id from school_applications where id = ?", (rs, n) -> new Object[] { day(rs.getDate(1)), rs.getString(2), iso(rs.getTimestamp(3)), rs.getObject(4) == null ? null : rs.getLong(4) }, id).get(0);
        Long enq = jdbc.query("select id from school_enquiries where application_id = ? and institution_id = ?", (rs, n) -> rs.getLong(1), id, inst()).stream().findFirst().orElse(null);
        List<DocRow> docs = jdbc.query("select id, doc_type, file_name, size_bytes, verified, uploaded_at from school_application_documents where application_id = ? order by id", (rs, n) -> new DocRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getBoolean(5), iso(rs.getTimestamp(6))), id);
        OfferRow offer = jdbc.query(OFFER_SELECT + " where o.application_id = ? and o.institution_id = ? order by o.id desc limit 1", (rs, n) -> offerRow(rs), id, inst()).stream().findFirst().orElse(null);
        return new AppDetail(row, (String) x[0], (String) x[1], (String) x[2], (Long) x[3], enq, docs, offer);
    }

    private record Clean(String name, LocalDate dob, Long yearId, Long classId, String guardian, String phone, String email) {}

    private Clean validate(AppBody b, boolean full) {
        long i = inst();
        Map<String, String> err = new LinkedHashMap<>();
        String name = clip(b.studentName(), 150);
        if (name == null) err.put("studentName", "Required.");
        LocalDate dob = date(b.dob(), "dob", err);
        if (dob != null && (dob.isAfter(LocalDate.now()) || dob.isBefore(LocalDate.now().minusYears(30)))) err.put("dob", "Check the date of birth.");
        if (full && dob == null && !err.containsKey("dob")) err.put("dob", "Required to submit.");
        Long year = b.academicYearId(), cls = b.classId();
        if (year != null && count("select count(*) from academic_years where id = ? and institution_id = ?", year, i) == 0) err.put("academicYearId", "Choose an academic year from this school.");
        if (cls != null && year != null && count("select count(*) from school_classes where id = ? and institution_id = ? and academic_year_id = ?", cls, i, year) == 0) err.put("classId", "That grade doesn't belong to the chosen academic year.");
        if (cls != null && year == null) err.put("academicYearId", "Choose the academic year first.");
        if (full) {
            if (year == null) err.putIfAbsent("academicYearId", "Required to submit.");
            if (cls == null) err.putIfAbsent("classId", "Required to submit.");
        }
        String guardian = clip(b.guardianName(), 150);
        if (full && guardian == null) err.put("guardianName", "Required to submit.");
        String ph = phone(b.phone(), err, full);
        String em = email(b.email(), "email", err);
        fail(err);
        return new Clean(name, dob, year, cls, guardian, ph, em);
    }

    private Date sqlDate(LocalDate d) { return d == null ? null : Date.valueOf(d); }

    public Map<String, Object> createApplication(AppBody b) {
        long i = inst();
        boolean submit = Boolean.TRUE.equals(b.submit());
        Clean c = validate(b, submit);
        Long enqId = b.enquiryId();
        if (enqId != null) {
            String st = jdbc.query("select status from school_enquiries where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), enqId, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Enquiry"));
            if (st.equals("CONVERTED")) throw ApiException.conflict("That enquiry is already linked to an application.");
        }
        lockNumbers();
        long next = count("select count(*) from school_applications where institution_id = ?", i) + 1;
        String no;
        do { no = "APP" + LocalDate.now().getYear() + "-" + String.format("%04d", next++); } while (count("select count(*) from school_applications where institution_id = ? and application_no = ?", i, no) > 0);
        Long id = jdbc.queryForObject("insert into school_applications (institution_id, application_no, student_name, grade, guardian_name, phone, guardian_email, dob, academic_year_id, class_id, stage) values (?, ?, ?, (select name from school_classes where id = ?), ?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                i, no, c.name(), c.classId(), c.guardian(), c.phone(), c.email(), sqlDate(c.dob()), c.yearId(), c.classId(), submit ? "SUBMITTED" : "DRAFT");
        if (enqId != null) jdbc.update("update school_enquiries set status = 'CONVERTED', application_id = ? where id = ? and institution_id = ?", id, enqId, i);
        audit.record(submit ? "APPLICATION_CREATED" : "APPLICATION_DRAFTED", "APPLICATION", id, i, (submit ? "New application submitted by " : "Application draft saved for ") + (c.guardian() == null ? c.name() : c.guardian()));
        if (submit) notifySubmitted(i);
        return Map.of("id", id, "applicationNo", no, "stage", submit ? "SUBMITTED" : "DRAFT");
    }

    public Map<String, Object> updateApplication(Long id, AppBody b) {
        long i = inst();
        String stage = jdbc.query("select stage from school_applications where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Application"));
        if (stage.equals("ADMITTED")) throw ApiException.conflict("An admitted application can't be edited.");
        boolean submit = Boolean.TRUE.equals(b.submit()) && stage.equals("DRAFT");
        Clean c = validate(b, submit || !stage.equals("DRAFT"));
        String newStage = submit ? "SUBMITTED" : stage;
        jdbc.update("update school_applications set student_name = ?, dob = ?, academic_year_id = ?, class_id = ?, grade = (select name from school_classes where id = ?), guardian_name = ?, phone = ?, guardian_email = ?, stage = ?, submitted_at = case when ? then now() else submitted_at end, updated_at = now() where id = ?",
                c.name(), sqlDate(c.dob()), c.yearId(), c.classId(), c.classId(), c.guardian(), c.phone(), c.email(), newStage, submit, id);
        audit.record(submit ? "APPLICATION_CREATED" : "APPLICATION_UPDATED", "APPLICATION", id, i, (submit ? "Application submitted: " : "Application updated: ") + c.name());
        if (submit && !"SUBMITTED".equals(stage)) notifySubmitted(i);
        return Map.of("id", id, "stage", newStage);
    }

    public Map<String, Object> submitApplication(Long id) {
        long i = inst();
        Object[] r = jdbc.query("select stage, student_name, dob, academic_year_id, class_id, guardian_name, phone, guardian_email from school_applications where id = ? and institution_id = ?",
                (rs, n) -> new Object[] { rs.getString(1), rs.getString(2), day(rs.getDate(3)), rs.getObject(4) == null ? null : rs.getLong(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getString(6), rs.getString(7), rs.getString(8) }, id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Application"));
        if (!"DRAFT".equals(r[0])) throw ApiException.conflict("Only a draft can be submitted.");
        validate(new AppBody((String) r[1], (String) r[2], (Long) r[3], (Long) r[4], (String) r[5], (String) r[6], (String) r[7], true, null), true);
        jdbc.update("update school_applications set stage = 'SUBMITTED', submitted_at = now(), updated_at = now() where id = ?", id);
        audit.record("APPLICATION_CREATED", "APPLICATION", id, i, "Application submitted: " + r[1]);
        notifySubmitted(i);
        return Map.of("id", id, "stage", "SUBMITTED");
    }

    private void notifySubmitted(long i) {
        Long n = jdbc.queryForObject("select count(*) from school_applications where institution_id = ? and stage = 'SUBMITTED'", Long.class, i);
        notify.add(i, "ADMISSIONS", "Admissions Review", n + " application" + (n == 1 ? " is" : "s are") + " pending review.", "/school/admissions");
    }

    public Map<String, Object> setStage(Long id, String stage) {
        long i = inst();
        String s = stage == null ? "" : stage.trim().toUpperCase();
        if (!MANUAL_STAGES.contains(s)) throw ApiException.badRequest("That status can't be set by hand. Offers and admission set their own.");
        Object[] r = jdbc.query("select stage, class_id from school_applications where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getObject(2) }, id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Application"));
        String cur = (String) r[0];
        if (cur.equals("DRAFT")) throw ApiException.conflict("Submit the application first.");
        if (cur.equals("ADMITTED")) throw ApiException.conflict("The student is already admitted.");
        if (cur.equals("OFFER_ISSUED")) throw ApiException.conflict("Withdraw the open offer before changing the status.");
        if (s.equals("WAITLISTED") && r[1] == null) throw ApiException.badRequest("Choose a grade for this application before adding it to the waiting list.");
        jdbc.update("update school_applications set stage = ?, waitlisted_at = case when ? then now() else null end, updated_at = now() where id = ?", s, s.equals("WAITLISTED"), id);
        audit.record("APPLICATION_STAGE", "APPLICATION", id, i, "Application moved to " + s.toLowerCase().replace('_', ' '));
        return Map.of("id", id, "stage", s);
    }

    public void deleteApplication(Long id) {
        long i = inst();
        String stage = jdbc.query("select stage from school_applications where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Application"));
        if (!stage.equals("DRAFT")) throw ApiException.conflict("Only drafts can be deleted. Use Reject or Withdraw for submitted applications.");
        jdbc.update("delete from school_applications where id = ?", id);
        audit.record("APPLICATION_DELETED", "APPLICATION", id, i, "Application draft deleted");
    }

    // ------------------------------------------------------------------ documents

    private static boolean magic(byte[] d, String type) {
        if (d.length < 4) return false;
        return switch (type) {
            case "application/pdf" -> d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "image/png" -> (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/jpeg" -> (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8 && (d[2] & 0xff) == 0xFF;
            default -> false;
        };
    }

    public DocRow addDocument(Long appId, String docType, MultipartFile file) {
        long i = inst();
        if (count("select count(*) from school_applications where id = ? and institution_id = ?", appId, i) == 0) throw ApiException.notFound("Application");
        String type = docType == null || docType.isBlank() ? "OTHER" : docType.trim().toUpperCase();
        if (!has(DOC_TYPES, type)) throw ApiException.badRequest("Unknown document type.");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to upload.");
        if (file.getSize() > MAX_DOC) throw ApiException.badRequest("Each file can be up to 5 MB.");
        if (count("select count(*) from school_application_documents where application_id = ?", appId) >= MAX_DOCS) throw ApiException.badRequest("An application can have up to " + MAX_DOCS + " documents.");
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (ct.equals("image/jpg")) ct = "image/jpeg";
        if (!Set.of("application/pdf", "image/png", "image/jpeg").contains(ct)) throw ApiException.badRequest("Use a PDF, PNG or JPG file.");
        byte[] data;
        try { data = file.getBytes(); } catch (java.io.IOException e) { throw ApiException.badRequest("Couldn't read that file."); }
        if (!magic(data, ct)) throw ApiException.badRequest("That file doesn't look like a real " + (ct.equals("application/pdf") ? "PDF" : "image") + ".");
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\"]+", "_").trim();
        if (name.isEmpty()) name = "document";
        if (name.length() > 200) name = name.substring(name.length() - 200);
        Long id = jdbc.queryForObject("insert into school_application_documents (institution_id, application_id, doc_type, file_name, content_type, size_bytes, data) values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class, i, appId, type, name, ct, (long) data.length, data);
        audit.record("APPLICATION_DOC_ADDED", "APPLICATION", appId, i, "Document uploaded: " + name);
        return new DocRow(id, type, name, data.length, false, java.time.Instant.now().toString());
    }

    /** {fileName, contentType, bytes} */
    @Transactional(readOnly = true)
    public Object[] documentFile(Long docId) {
        return jdbc.query("select file_name, content_type, data from school_application_documents where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getString(2), rs.getBytes(3) }, docId, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Document"));
    }

    public Map<String, Object> verifyDocument(Long docId, Boolean verified) {
        if (verified == null) throw ApiException.badRequest("Choose verified or not verified.");
        long i = inst();
        Object[] d = jdbc.query("select application_id, file_name from school_application_documents where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getLong(1), rs.getString(2) }, docId, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Document"));
        jdbc.update("update school_application_documents set verified = ?, verified_by = ? where id = ?", verified, verified ? actor() : null, docId);
        audit.record(verified ? "APPLICATION_DOC_VERIFIED" : "APPLICATION_DOC_UNVERIFIED", "APPLICATION", (Long) d[0], i, (verified ? "Document verified: " : "Document unverified: ") + d[1]);
        return Map.of("id", docId, "verified", verified);
    }

    public void deleteDocument(Long docId) {
        long i = inst();
        Object[] d = jdbc.query("select application_id, file_name from school_application_documents where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getLong(1), rs.getString(2) }, docId, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Document"));
        jdbc.update("delete from school_application_documents where id = ?", docId);
        audit.record("APPLICATION_DOC_REMOVED", "APPLICATION", (Long) d[0], i, "Document removed: " + d[1]);
    }

    // ------------------------------------------------------------------ enquiries

    private static final String ENQ_COLS = "e.id, 'ENQ-' || lpad(e.id::text, 4, '0'), e.name, e.phone, e.email, e.child_name, e.grade_interest, e.source, e.status, e.follow_up_on, e.note, e.application_id, e.created_at";
    private static EnqRow enqRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new EnqRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), day(rs.getDate(10)), rs.getString(11), rs.getObject(12) == null ? null : rs.getLong(12), iso(rs.getTimestamp(13)));
    }

    @Transactional(readOnly = true)
    public EnqPage enquiries(String status, String q, int page, int size) {
        List<String> w = new ArrayList<>(List.of("e.institution_id = ?"));
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (status != null && !status.isBlank()) { if (!ENQ_STATUS.contains(status)) throw ApiException.badRequest("Unknown status."); w.add("e.status = ?"); a.add(status); }
        if (q != null && !q.isBlank()) { w.add("(e.name ilike ? or coalesce(e.child_name,'') ilike ? or coalesce(e.phone,'') ilike ?)"); a.add(like(q)); a.add(like(q)); a.add(like(q)); }
        String where = " where " + String.join(" and ", w);
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        long total = count("select count(*) from school_enquiries e" + where, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        List<EnqRow> items = jdbc.query("select " + ENQ_COLS + " from school_enquiries e" + where + " order by e.created_at desc, e.id desc limit ? offset ?", (rs, n) -> enqRow(rs), a.toArray());
        return new EnqPage(items, total, pg, sz);
    }

    private Object[] cleanEnquiry(EnqBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = clip(b.name(), 150);
        if (name == null) err.put("name", "Required.");
        String ph = phone(b.phone(), err, false);
        String em = email(b.email(), "email", err);
        LocalDate fu = date(b.followUpOn(), "followUpOn", err);
        String src = b.source() == null || b.source().isBlank() ? null : b.source().trim().toUpperCase();
        if (src != null && !has(SOURCES, src)) err.put("source", "Choose one of the listed sources.");
        fail(err);
        return new Object[] { name, ph, em, clip(b.childName(), 150), clip(b.grade(), 40), src, sqlDate(fu), clip(b.note(), 500) };
    }

    public Map<String, Object> createEnquiry(EnqBody b) {
        long i = inst();
        Object[] c = cleanEnquiry(b);
        Long id = jdbc.queryForObject("insert into school_enquiries (institution_id, name, phone, email, child_name, grade_interest, source, follow_up_on, note) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class, i, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]);
        audit.record("ENQUIRY_CREATED", "ENQUIRY", id, i, "New enquiry from " + c[0]);
        return Map.of("id", id);
    }

    public Map<String, Object> updateEnquiry(Long id, EnqBody b) {
        long i = inst();
        Object[] c = cleanEnquiry(b);
        if (jdbc.update("update school_enquiries set name = ?, phone = ?, email = ?, child_name = ?, grade_interest = ?, source = ?, follow_up_on = ?, note = ? where id = ? and institution_id = ?", c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], id, i) == 0) throw ApiException.notFound("Enquiry");
        audit.record("ENQUIRY_UPDATED", "ENQUIRY", id, i, "Enquiry updated: " + c[0]);
        return Map.of("id", id);
    }

    public Map<String, Object> setEnquiryStatus(Long id, String status) {
        long i = inst();
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("NEW", "FOLLOW_UP", "CLOSED").contains(s)) throw ApiException.badRequest("Choose Open, Contacted or Closed. Converting happens when you create the application.");
        String cur = jdbc.query("select status from school_enquiries where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Enquiry"));
        if (cur.equals("CONVERTED")) throw ApiException.conflict("This enquiry has already become an application.");
        jdbc.update("update school_enquiries set status = ? where id = ?", s, id);
        audit.record("ENQUIRY_UPDATED", "ENQUIRY", id, i, "Enquiry marked " + s.toLowerCase().replace('_', ' '));
        return Map.of("id", id, "status", s);
    }

    public void deleteEnquiry(Long id) {
        long i = inst();
        if (jdbc.update("delete from school_enquiries where id = ? and institution_id = ? and status <> 'CONVERTED'", id, i) == 0) throw ApiException.conflict("That enquiry doesn't exist or is already linked to an application.");
        audit.record("ENQUIRY_DELETED", "ENQUIRY", id, i, "Enquiry deleted");
    }

    // ------------------------------------------------------------------ waiting list

    @Transactional(readOnly = true)
    public Waitlist waitlist(Long classId) {
        long i = inst();
        if (classId == null) throw ApiException.badRequest("Choose a grade.");
        if (count("select count(*) from school_classes where id = ? and institution_id = ?", classId, i) == 0) throw ApiException.notFound("Grade");
        List<WaitRow> items = new ArrayList<>();
        jdbc.query("select a.id, a.application_no, a.student_name, c.name, a.waitlisted_at from school_applications a join school_classes c on c.id = a.class_id where a.institution_id = ? and a.class_id = ? and a.stage = 'WAITLISTED' order by a.waitlisted_at, a.id",
                rs -> { items.add(new WaitRow(items.size() + 1, rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), iso(rs.getTimestamp(5)))); }, i, classId);
        return new Waitlist(seatsAvailable(classId), items);
    }

    /** Seats left = section limits minus enrolled students minus open offers. Null when no section of the grade has a limit. */
    private Integer seatsAvailable(long classId) {
        long capped = count("select count(*) from sections where school_class_id = ? and active and max_students is not null", classId);
        if (capped == 0) return null;
        long cap = count("select coalesce(sum(max_students), 0) from sections where school_class_id = ? and active and max_students is not null", classId);
        long enrolled = count("select count(*) from students where school_class_id = ? and active", classId);
        long reserved = count("select count(*) from school_offers o join school_applications a on a.id = o.application_id where a.class_id = ? and a.stage = 'OFFER_ISSUED' and o.status in ('PENDING','SENT','ACCEPTED')", classId);
        return (int) Math.max(0, cap - enrolled - reserved);
    }

    // ------------------------------------------------------------------ offers

    private static final String OFFER_SELECT = "select o.id, 'OFR-' || lpad(o.id::text, 4, '0'), a.id, a.application_no, a.student_name, c.name, o.expires_on,"
            + " case when o.status = 'ACCEPTED' and a.stage = 'ADMITTED' then 'ADMITTED' else o.status end, o.sent_at, o.responded_at, o.note, a.class_id"
            + " from school_offers o join school_applications a on a.id = o.application_id left join school_classes c on c.id = a.class_id";

    private static OfferRow offerRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OfferRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), day(rs.getDate(7)), rs.getString(8), iso(rs.getTimestamp(9)), iso(rs.getTimestamp(10)), rs.getString(11), rs.getObject(12) == null ? null : rs.getLong(12));
    }

    /** Offers that passed their date without an answer are closed, and the application goes back to review. */
    private void expireOffers() {
        long i = inst();
        List<Long> apps = jdbc.query("select application_id from school_offers where institution_id = ? and status in ('PENDING','SENT') and expires_on < current_date", (rs, n) -> rs.getLong(1), i);
        if (apps.isEmpty()) return;
        jdbc.update("update school_offers set status = 'EXPIRED', responded_at = now() where institution_id = ? and status in ('PENDING','SENT') and expires_on < current_date", i);
        for (Long a : apps) jdbc.update("update school_applications set stage = 'UNDER_REVIEW', updated_at = now() where id = ? and stage = 'OFFER_ISSUED'", a);
    }

    public OfferPage offers(String status, String q, int page, int size) {
        long i = inst();
        expireOffers();
        List<String> w = new ArrayList<>(List.of("o.institution_id = ?"));
        List<Object> a = new ArrayList<>(List.of(i));
        if (status != null && !status.isBlank()) {
            if (!Set.of("PENDING", "SENT", "ACCEPTED", "DECLINED", "WITHDRAWN", "EXPIRED", "ADMITTED").contains(status)) throw ApiException.badRequest("Unknown status.");
            if (status.equals("ADMITTED")) w.add("o.status = 'ACCEPTED' and a.stage = 'ADMITTED'");
            else if (status.equals("ACCEPTED")) w.add("o.status = 'ACCEPTED' and a.stage <> 'ADMITTED'");
            else { w.add("o.status = ?"); a.add(status); }
        }
        if (q != null && !q.isBlank()) { w.add("(a.student_name ilike ? or a.application_no ilike ?)"); a.add(like(q)); a.add(like(q)); }
        String where = " where " + String.join(" and ", w);
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        long total = count("select count(*) from school_offers o join school_applications a on a.id = o.application_id" + where, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        List<OfferRow> items = jdbc.query(OFFER_SELECT + where + " order by o.created_at desc, o.id desc limit ? offset ?", (rs, n) -> offerRow(rs), a.toArray());
        return new OfferPage(items, total, pg, sz);
    }

    public Map<String, Object> createOffer(OfferBody b) {
        long i = inst();
        expireOffers();
        Map<String, String> err = new LinkedHashMap<>();
        Object[] app = b.applicationId() == null ? null : jdbc.query("select stage, class_id, student_name from school_applications where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getObject(2), rs.getString(3) }, b.applicationId(), i).stream().findFirst().orElse(null);
        if (app == null) err.put("applicationId", "Choose an application from this school.");
        else if (!OFFERABLE.contains((String) app[0])) err.put("applicationId", "This application can't receive an offer in its current status.");
        else if (app[1] == null) err.put("applicationId", "Set a grade on the application first.");
        LocalDate exp = date(b.expiresOn(), "expiresOn", err);
        if (exp == null && !err.containsKey("expiresOn")) err.put("expiresOn", "Required.");
        if (exp != null && (exp.isBefore(LocalDate.now()) || exp.isAfter(LocalDate.now().plusDays(120)))) err.put("expiresOn", "Choose a date within the next 120 days.");
        fail(err);
        if (count("select count(*) from school_offers where application_id = ? and status in ('PENDING','SENT','ACCEPTED')", b.applicationId()) > 0) throw ApiException.conflict("This application already has an open offer.");
        Long id = jdbc.queryForObject("insert into school_offers (institution_id, application_id, expires_on, note, created_by) values (?, ?, ?, ?, ?) returning id", Long.class, i, b.applicationId(), Date.valueOf(exp), clip(b.note(), 300), actor());
        jdbc.update("update school_applications set stage = 'OFFER_ISSUED', waitlisted_at = null, updated_at = now() where id = ?", b.applicationId());
        audit.record("OFFER_CREATED", "OFFER", id, i, "Offer created for " + app[2]);
        return Map.of("id", id);
    }

    private Object[] offer(Long id) {
        return jdbc.query("select o.status, o.application_id, a.student_name, a.stage, a.class_id, o.expires_on from school_offers o join school_applications a on a.id = o.application_id where o.id = ? and o.institution_id = ?",
                (rs, n) -> new Object[] { rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getDate(6).toLocalDate() }, id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Offer"));
    }

    public Map<String, Object> sendOffer(Long id) {
        expireOffers();
        Object[] o = offer(id);
        if (!"PENDING".equals(o[0])) throw ApiException.conflict("Only a pending offer can be sent.");
        jdbc.update("update school_offers set status = 'SENT', sent_at = now() where id = ?", id);
        audit.record("OFFER_SENT", "OFFER", id, inst(), "Offer marked as sent to the family of " + o[2]);
        return Map.of("id", id, "status", "SENT");
    }

    public Map<String, Object> respond(Long id, boolean accept) {
        expireOffers();
        Object[] o = offer(id);
        if (!"SENT".equals(o[0])) throw ApiException.conflict(accept ? "Mark the offer as sent before recording an acceptance." : "Only a sent offer can be declined.");
        jdbc.update("update school_offers set status = ?, responded_at = now() where id = ?", accept ? "ACCEPTED" : "DECLINED", id);
        if (!accept) jdbc.update("update school_applications set stage = 'WITHDRAWN', updated_at = now() where id = ? and stage = 'OFFER_ISSUED'", o[1]);
        audit.record(accept ? "OFFER_ACCEPTED" : "OFFER_DECLINED", "OFFER", id, inst(), "Offer " + (accept ? "accepted" : "declined") + " - " + o[2]);
        return Map.of("id", id, "status", accept ? "ACCEPTED" : "DECLINED");
    }

    public Map<String, Object> withdrawOffer(Long id) {
        expireOffers();
        Object[] o = offer(id);
        if (!Set.of("PENDING", "SENT", "ACCEPTED").contains(o[0]) || "ADMITTED".equals(o[3])) throw ApiException.conflict("This offer can't be withdrawn.");
        jdbc.update("update school_offers set status = 'WITHDRAWN', responded_at = now() where id = ?", id);
        jdbc.update("update school_applications set stage = 'UNDER_REVIEW', updated_at = now() where id = ? and stage = 'OFFER_ISSUED'", o[1]);
        audit.record("OFFER_WITHDRAWN", "OFFER", id, inst(), "Offer withdrawn - " + o[2]);
        return Map.of("id", id, "status", "WITHDRAWN");
    }

    /** Turns an accepted offer into a student: creates the student record in the chosen section. */
    public Map<String, Object> admit(Long offerId, Long sectionId) {
        long i = inst();
        expireOffers();
        Object[] o = offer(offerId);
        if (!"ACCEPTED".equals(o[0])) throw ApiException.conflict("Only an accepted offer can be turned into an admission.");
        if (!"OFFER_ISSUED".equals(o[3])) throw ApiException.conflict("This student is already admitted.");
        if (o[4] == null) throw ApiException.badRequest("The application has no grade.");
        if (sectionId == null) throw new FieldErrorsException(Map.of("sectionId", "Choose a section."));
        Object[] sec = jdbc.query("select s.max_students, (select count(*) from students st where st.section_id = s.id and st.active), s.name from sections s where s.id = ? and s.institution_id = ? and s.school_class_id = ? and s.active",
                (rs, n) -> new Object[] { rs.getObject(1) == null ? null : rs.getInt(1), rs.getLong(2), rs.getString(3) }, sectionId, i, o[4]).stream().findFirst().orElseThrow(() -> new FieldErrorsException(Map.of("sectionId", "Choose a section of this grade.")));
        if (sec[0] != null && (Long) sec[1] >= (Integer) sec[0]) throw new FieldErrorsException(Map.of("sectionId", "Section " + sec[2] + " is full."));
        lockNumbers();
        long next = count("select coalesce(max(substring(admission_no from 5)::bigint), 1000) from students where institution_id = ? and admission_no ~ '^ADM-[0-9]+$'", i) + 1;
        String no = "ADM-" + next;
        // carry the application's details over so the student record starts complete (relationship isn't collected on applications)
        Long sid = jdbc.queryForObject("insert into students (institution_id, admission_no, full_name, school_class_id, section_id, dob, joining_date, guardian_name, guardian_phone, guardian_email) "
                + "select ?::bigint, ?::varchar, ?::varchar, ?::bigint, ?::bigint, a.dob, current_date, a.guardian_name, a.phone, a.guardian_email from school_applications a where a.id = ? returning id", Long.class, i, no, o[2], o[4], sectionId, o[1]);
        jdbc.update("insert into student_documents (institution_id, student_id, doc_type, file_name, content_type, size_bytes, data, uploaded_by) select institution_id, ?::bigint, doc_type, file_name, content_type, size_bytes, data, ?::varchar from school_application_documents where application_id = ?", sid, actor(), o[1]);
        jdbc.update("update school_applications set stage = 'ADMITTED', student_id = ?, admitted_at = now(), updated_at = now() where id = ?", sid, o[1]);
        audit.record("STUDENT_ADMITTED", "APPLICATION", (Long) o[1], i, "Admitted " + o[2] + " as " + no);
        return Map.of("studentId", sid, "admissionNo", no);
    }

    // ------------------------------------------------------------------ admitted

    @Transactional(readOnly = true)
    public AdmittedPage admitted(Long classId, String q, int page, int size) {
        List<String> w = new ArrayList<>(List.of("a.institution_id = ?", "a.stage = 'ADMITTED'"));
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (classId != null) { w.add("a.class_id = ?"); a.add(classId); }
        if (q != null && !q.isBlank()) { w.add("(a.student_name ilike ? or coalesce(st.admission_no,'') ilike ?)"); a.add(like(q)); a.add(like(q)); }
        String from = " from school_applications a left join students st on st.id = a.student_id left join school_classes c on c.id = a.class_id left join sections s on s.id = st.section_id where " + String.join(" and ", w);
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        long total = count("select count(*)" + from, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        List<AdmittedRow> items = jdbc.query("select a.id, st.admission_no, a.student_id, a.student_name, c.name, s.name, a.admitted_at" + from + " order by a.admitted_at desc nulls last, a.id desc limit ? offset ?",
                (rs, n) -> new AdmittedRow(rs.getLong(1), rs.getString(2), rs.getObject(3) == null ? null : rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), iso(rs.getTimestamp(7))), a.toArray());
        return new AdmittedPage(items, total, pg, sz);
    }
}
