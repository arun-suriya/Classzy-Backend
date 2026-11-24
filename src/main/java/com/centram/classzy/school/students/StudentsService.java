package com.centram.classzy.school.students;

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
import com.centram.classzy.school.students.StuDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Student records for one school. Every query is limited to CurrentUser.institutionId(). Students are deactivated, never deleted. */
@Service
@Transactional
public class StudentsService {

    static final List<Code> RELATIONS = List.of(new Code("FATHER", "Father"), new Code("MOTHER", "Mother"), new Code("GUARDIAN", "Guardian"), new Code("GRANDPARENT", "Grandparent"), new Code("SIBLING", "Sibling"), new Code("OTHER", "Other"));
    static final List<Code> DOC_TYPES = List.of(new Code("BIRTH", "Birth certificate"), new Code("REPORT_CARD", "Previous report card"), new Code("TRANSFER", "Transfer certificate"), new Code("ID_PROOF", "ID proof"), new Code("PHOTO", "Photograph"), new Code("OTHER", "Other"));
    static final long MAX_DOC = 5L * 1024 * 1024;
    static final int MAX_DOCS = 10;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public StudentsService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) {
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
    }

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }
    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }
    private static String day(Date d) { return d == null ? null : d.toLocalDate().toString(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static String clip(String v, int max) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t); }
    private static String like(String q) { return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
    private static boolean has(List<Code> l, String code) { return l.stream().anyMatch(c -> c.code().equals(code)); }
    private static LocalDate date(String v, String key, Map<String, String> err) {
        if (v == null || v.isBlank()) { err.put(key, "Required."); return null; }
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { err.put(key, "Use a date like 2026-10-12."); return null; }
    }
    private void lockNumbers() { jdbc.queryForObject("select pg_advisory_xact_lock(hashtext('adm-' || ?))::text", String.class, String.valueOf(inst())); }

    @Transactional(readOnly = true)
    public Options options() { return new Options(RELATIONS, DOC_TYPES); }

    // ------------------------------------------------------------------ list

    private static final String COLS = "s.id, s.admission_no, s.full_name, c.name, s.school_class_id, c.academic_year_id, sec.name, s.section_id, s.active";
    private static final String FROM = " from students s left join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id";
    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getObject(6) == null ? null : rs.getLong(6), rs.getString(7), rs.getObject(8) == null ? null : rs.getLong(8), rs.getBoolean(9));
    }

    @Transactional(readOnly = true)
    public Page list(Long yearId, Long classId, Long sectionId, String status, String q, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<String> w = new ArrayList<>(List.of("s.institution_id = ?"));
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (yearId != null) { w.add("c.academic_year_id = ?"); a.add(yearId); }
        if (classId != null) { w.add("s.school_class_id = ?"); a.add(classId); }
        if (sectionId != null) { w.add("s.section_id = ?"); a.add(sectionId); }
        if (q != null && !q.isBlank()) { w.add("(lower(s.full_name) like lower(?) escape '\\' or lower(s.admission_no) like lower(?) escape '\\' or lower(coalesce(s.guardian_name, '')) like lower(?) escape '\\')"); String l = like(q); a.add(l); a.add(l); a.add(l); }
        // counts ignore the status filter so the chips stay meaningful
        String base = String.join(" and ", w);
        long active = count("select count(*)" + FROM + " where " + base + " and s.active", a.toArray());
        long inactive = count("select count(*)" + FROM + " where " + base + " and not s.active", a.toArray());
        if ("ACTIVE".equalsIgnoreCase(status)) w.add("s.active");
        else if ("INACTIVE".equalsIgnoreCase(status)) w.add("not s.active");
        String where = String.join(" and ", w);
        long total = count("select count(*)" + FROM + " where " + where, a.toArray());
        List<Object> pa = new ArrayList<>(a); pa.add(sz); pa.add((long) pg * sz);
        List<Row> items = jdbc.query("select " + COLS + FROM + " where " + where + " order by s.full_name, s.id limit ? offset ?", (rs, n) -> row(rs), pa.toArray());
        return new Page(items, total, pg, sz, active, inactive);
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return jdbc.query("select " + COLS + ", y.name, s.dob, s.joining_date, s.guardian_name, s.guardian_relation, s.guardian_phone, s.guardian_email, s.address, s.inactive_reason, (select a.id from school_applications a where a.student_id = s.id limit 1), s.created_at"
                + FROM + " left join academic_years y on y.id = c.academic_year_id where s.id = ? and s.institution_id = ?", (rs, n) -> new Detail(row(rs), rs.getString(10), day(rs.getDate(11)), day(rs.getDate(12)), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18),
                rs.getObject(19) == null ? null : rs.getLong(19), iso(rs.getTimestamp(20)), docs(id)), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Student"));
    }

    private List<Doc> docs(long id) {
        return jdbc.query("select id, doc_type, file_name, size_bytes, created_at from student_documents where student_id = ? order by id", (rs, n) -> new Doc(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), iso(rs.getTimestamp(5))), id);
    }

    // ------------------------------------------------------------------ create / update

    private record Clean(String name, LocalDate dob, Long classId, Long sectionId, LocalDate joining, String guardian, String relation, String phone, String email, String address) {}

    private Clean check(Body b, Long selfId) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        String name = clip(b.fullName(), 200);
        if (name == null) e.put("fullName", "Required.");
        LocalDate dob = date(b.dob(), "dob", e);
        if (dob != null && !dob.isBefore(LocalDate.now())) e.put("dob", "Date of birth must be in the past.");
        if (dob != null && dob.isBefore(LocalDate.of(1980, 1, 1))) e.put("dob", "Check the date of birth.");
        LocalDate joining = date(b.joiningDate(), "joiningDate", e);
        if (joining != null && dob != null && !joining.isAfter(dob)) e.put("joiningDate", "Joining date must be after the date of birth.");
        if (joining != null && joining.isAfter(LocalDate.now().plusYears(1))) e.put("joiningDate", "Joining date is too far ahead.");
        if (b.academicYearId() == null) e.put("academicYearId", "Required.");
        if (b.classId() == null) e.put("classId", "Required.");
        if (b.sectionId() == null) e.put("sectionId", "Required.");
        if (b.academicYearId() != null && b.classId() != null && count("select count(*) from school_classes where id = ? and institution_id = ? and academic_year_id = ? and active", b.classId(), i, b.academicYearId()) == 0) e.put("classId", "Choose a grade of that academic year.");
        if (b.classId() != null && b.sectionId() != null && !e.containsKey("classId")) {
            Object[] sec = jdbc.query("select s.max_students, (select count(*) from students st where st.section_id = s.id and st.active and st.id <> ?), s.name from sections s where s.id = ? and s.institution_id = ? and s.school_class_id = ? and s.active",
                    (rs, n) -> new Object[] { rs.getObject(1) == null ? null : rs.getInt(1), rs.getLong(2), rs.getString(3) }, selfId == null ? -1L : selfId, b.sectionId(), i, b.classId()).stream().findFirst().orElse(null);
            if (sec == null) e.put("sectionId", "Choose a section of that grade.");
            else if (sec[0] != null && (Long) sec[1] >= (Integer) sec[0]) e.put("sectionId", "Section " + sec[2] + " is full.");
        }
        String guardian = clip(b.guardianName(), 200);
        if (guardian == null) e.put("guardianName", "Required.");
        String rel = b.relationship() == null ? null : b.relationship().trim().toUpperCase();
        if (rel == null || rel.isEmpty()) e.put("relationship", "Required."); else if (!has(RELATIONS, rel)) e.put("relationship", "Choose from the list.");
        String phone = clip(b.phone(), 30);
        if (phone == null) e.put("phone", "Required."); else if (!phone.replaceAll("[ ()-]", "").matches("\\+?[0-9]{7,15}")) e.put("phone", "Enter a valid mobile number.");
        String email = clip(b.email(), 200);
        if (email == null) e.put("email", "Required."); else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) e.put("email", "Enter a valid email address.");
        String address = clip(b.address(), 500);
        if (address == null) e.put("address", "Required.");
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        return new Clean(name, dob, b.classId(), b.sectionId(), joining, guardian, rel, phone, email, address);
    }

    public Map<String, Object> create(Body b) {
        long i = inst();
        Clean c = check(b, null);
        lockNumbers();
        long next = count("select coalesce(max(substring(admission_no from 5)::bigint), 1000) from students where institution_id = ? and admission_no ~ '^ADM-[0-9]+$'", i) + 1;
        String no = "ADM-" + next;
        Long id = jdbc.queryForObject("insert into students (institution_id, admission_no, full_name, school_class_id, section_id, dob, joining_date, guardian_name, guardian_relation, guardian_phone, guardian_email, address) values (?,?,?,?,?,?,?,?,?,?,?,?) returning id",
                Long.class, i, no, c.name(), c.classId(), c.sectionId(), Date.valueOf(c.dob()), Date.valueOf(c.joining()), c.guardian(), c.relation(), c.phone(), c.email(), c.address());
        audit.record("STUDENT_CREATED", "STUDENT", id, i, "Student " + c.name() + " added as " + no);
        return Map.of("id", id, "admissionNo", no);
    }

    public Map<String, Object> update(Long id, Body b) {
        long i = inst();
        Object[] cur = jdbc.query("select admission_no, active from students where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getBoolean(2) }, id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Student"));
        Clean c = check(b, id);
        jdbc.update("update students set full_name=?, school_class_id=?, section_id=?, dob=?, joining_date=?, guardian_name=?, guardian_relation=?, guardian_phone=?, guardian_email=?, address=?, updated_at=now() where id=?",
                c.name(), c.classId(), c.sectionId(), Date.valueOf(c.dob()), Date.valueOf(c.joining()), c.guardian(), c.relation(), c.phone(), c.email(), c.address(), id);
        audit.record("STUDENT_UPDATED", "STUDENT", id, i, "Student " + cur[0] + " updated");
        return Map.of("id", id, "admissionNo", cur[0]);
    }

    public Map<String, Object> setStatus(Long id, Boolean active, String reason) {
        long i = inst();
        if (active == null) throw ApiException.badRequest("Choose active or inactive.");
        Object[] cur = jdbc.query("select admission_no, active, section_id from students where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getBoolean(2), rs.getObject(3) == null ? null : rs.getLong(3) }, id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Student"));
        if (active && !(Boolean) cur[1] && cur[2] != null) {
            Object[] sec = jdbc.query("select s.max_students, (select count(*) from students st where st.section_id = s.id and st.active), s.name from sections s where s.id = ?", (rs, n) -> new Object[] { rs.getObject(1) == null ? null : rs.getInt(1), rs.getLong(2), rs.getString(3) }, cur[2]).stream().findFirst().orElse(null);
            if (sec != null && sec[0] != null && (Long) sec[1] >= (Integer) sec[0]) throw ApiException.conflict("Section " + sec[2] + " is full, so this student can't be made active. Move them to another section first.");
        }
        String why = active ? null : clip(reason, 200);
        jdbc.update("update students set active = ?, inactive_reason = ?, updated_at = now() where id = ?", active, why, id);
        audit.record(active ? "STUDENT_ACTIVATED" : "STUDENT_DEACTIVATED", "STUDENT", id, i, "Student " + cur[0] + (active ? " set active" : " set inactive" + (why == null ? "" : ": " + why)));
        return Map.of("id", id, "active", active);
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

    public Doc addDocument(Long studentId, String docType, MultipartFile file) {
        long i = inst();
        if (count("select count(*) from students where id = ? and institution_id = ?", studentId, i) == 0) throw ApiException.notFound("Student");
        String type = docType == null || docType.isBlank() ? "OTHER" : docType.trim().toUpperCase();
        if (!has(DOC_TYPES, type)) throw ApiException.badRequest("Unknown document type.");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to upload.");
        if (file.getSize() > MAX_DOC) throw ApiException.badRequest("Each file can be up to 5 MB.");
        if (count("select count(*) from student_documents where student_id = ?", studentId) >= MAX_DOCS) throw ApiException.badRequest("A student can have up to " + MAX_DOCS + " documents.");
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (ct.equals("image/jpg")) ct = "image/jpeg";
        if (!Set.of("application/pdf", "image/png", "image/jpeg").contains(ct)) throw ApiException.badRequest("Use a PDF, PNG or JPG file.");
        byte[] data;
        try { data = file.getBytes(); } catch (java.io.IOException e) { throw ApiException.badRequest("Couldn't read that file."); }
        if (!magic(data, ct)) throw ApiException.badRequest("That file doesn't look like a real " + (ct.equals("application/pdf") ? "PDF" : "image") + ".");
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n\"]+", "_").trim();
        if (name.isEmpty()) name = "document";
        if (name.length() > 200) name = name.substring(name.length() - 200);
        Long id = jdbc.queryForObject("insert into student_documents (institution_id, student_id, doc_type, file_name, content_type, size_bytes, data, uploaded_by) values (?,?,?,?,?,?,?,?) returning id", Long.class, i, studentId, type, name, ct, (long) data.length, data, actor());
        audit.record("STUDENT_DOC_ADDED", "STUDENT", studentId, i, "Document uploaded: " + name);
        return new Doc(id, type, name, data.length, java.time.Instant.now().toString());
    }

    @Transactional(readOnly = true)
    public Object[] documentFile(Long docId) {
        return jdbc.query("select file_name, content_type, data from student_documents where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getString(2), rs.getBytes(3) }, docId, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Document"));
    }

    public void deleteDocument(Long docId) {
        long i = inst();
        Object[] d = jdbc.query("select student_id, file_name from student_documents where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getLong(1), rs.getString(2) }, docId, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Document"));
        jdbc.update("delete from student_documents where id = ?", docId);
        audit.record("STUDENT_DOC_REMOVED", "STUDENT", (Long) d[0], i, "Document removed: " + d[1]);
    }
}
