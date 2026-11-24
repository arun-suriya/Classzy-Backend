package com.centram.classzy.school.academics;

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
import com.centram.classzy.school.academics.AcaDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Homework and assignments for one school. Nothing is sent to students or parents: "Published" is a status the school
 * controls. Submissions and marks are entered by the admin; there is no student or teacher login for this yet.
 */
@Service
@Transactional
public class WorkService {

    static final Set<String> STATUS = Set.of("DRAFT", "PUBLISHED");

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;
    private final AcademicsService base;

    public WorkService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, AcademicsService base) {
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
        this.base = base;
    }

    private long inst() { return current.institutionId(); }
    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }
    private static String day(Date d) { return d == null ? null : d.toLocalDate().toString(); }

    private record Clean(long sectionId, long subjectId, String title, String description, LocalDate due, String status) {}

    private Clean check(Long sectionId, Long subjectId, String title, String description, String due, String status, boolean newItem, Map<String, String> e) {
        long i = inst();
        String t = AcademicsService.clip(title, 200);
        if (t == null) e.put("title", "Required.");
        LocalDate d = null;
        if (due == null || due.isBlank()) e.put("dueDate", "Required.");
        else try { d = LocalDate.parse(due.trim()); } catch (DateTimeParseException x) { e.put("dueDate", "Use a date like 2026-11-20."); }
        if (d != null && newItem && d.isBefore(LocalDate.now())) e.put("dueDate", "Due date can't be in the past.");
        if (d != null && d.isAfter(LocalDate.now().plusYears(1))) e.put("dueDate", "Due date is too far ahead.");
        String st = status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase();
        if (!STATUS.contains(st)) e.put("status", "Choose Draft or Published.");
        if (sectionId == null) e.put("sectionId", "Required.");
        if (subjectId == null) e.put("subjectId", "Required.");
        if (sectionId != null && subjectId != null && !e.containsKey("sectionId")) {
            Long classId = jdbc.query("select school_class_id from sections where id = ? and institution_id = ? and active", (rs, n) -> rs.getLong(1), sectionId, i).stream().findFirst().orElse(null);
            if (classId == null) e.put("sectionId", "Choose a class from the list.");
            else if (base.count("select count(*) from class_subjects where class_id = ? and subject_id = ?", classId, subjectId) == 0) e.put("subjectId", "This subject isn't taught in that grade. Add it on the Subjects tab first.");
        }
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        return new Clean(sectionId, subjectId, t, AcademicsService.clip(description, 2000), d, st);
    }

    private static String where(Long classId, Long sectionId, Long subjectId, String status, String alias, List<Object> a) {
        StringBuilder w = new StringBuilder(" where " + alias + ".institution_id = ?");
        if (classId != null) { w.append(" and sec.school_class_id = ?"); a.add(classId); }
        if (sectionId != null) { w.append(" and " + alias + ".section_id = ?"); a.add(sectionId); }
        if (subjectId != null) { w.append(" and " + alias + ".subject_id = ?"); a.add(subjectId); }
        if (status != null && !status.isBlank()) { w.append(" and " + alias + ".status = ?"); a.add(status.trim().toUpperCase()); }
        return w.toString();
    }

    // ------------------------------------------------------------------ homework

    private static final String HW_FROM = " from school_homework h join sections sec on sec.id = h.section_id join school_classes c on c.id = sec.school_class_id join subjects s on s.id = h.subject_id";
    private static final String HW_COLS = "select h.id, h.title, h.description, h.section_id, sec.school_class_id, c.name, sec.name, h.subject_id, s.name, h.due_date, h.status, h.created_by, h.created_at";
    private static HwRow hw(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HwRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9), day(rs.getDate(10)), rs.getString(11), rs.getString(12), iso(rs.getTimestamp(13)));
    }

    @Transactional(readOnly = true)
    public HwPage homework(Long classId, Long sectionId, Long subjectId, String status, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<Object> a = new ArrayList<>(List.of(inst()));
        String w = where(classId, sectionId, subjectId, status, "h", a);
        long total = base.count("select count(*)" + HW_FROM + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        return new HwPage(jdbc.query(HW_COLS + HW_FROM + w + " order by h.due_date desc, h.id desc limit ? offset ?", (rs, n) -> hw(rs), a.toArray()), total, pg, sz);
    }

    public Map<String, Object> createHomework(HwBody b) {
        Clean c = check(b.sectionId(), b.subjectId(), b.title(), b.description(), b.dueDate(), b.status(), true, new LinkedHashMap<>());
        Long id = jdbc.queryForObject("insert into school_homework (institution_id, section_id, subject_id, title, description, due_date, status, created_by) values (?,?,?,?,?,?,?,?) returning id", Long.class,
                inst(), c.sectionId(), c.subjectId(), c.title(), c.description(), Date.valueOf(c.due()), c.status(), current.principal().email());
        audit.record("HOMEWORK_CREATED", "HOMEWORK", id, inst(), "Homework \"" + c.title() + "\" created as " + c.status().toLowerCase());
        return Map.of("id", id);
    }

    public Map<String, Object> updateHomework(Long id, HwBody b) {
        existing("school_homework", id);
        Clean c = check(b.sectionId(), b.subjectId(), b.title(), b.description(), b.dueDate(), b.status(), false, new LinkedHashMap<>());
        jdbc.update("update school_homework set section_id=?, subject_id=?, title=?, description=?, due_date=?, status=?, updated_at=now() where id=?", c.sectionId(), c.subjectId(), c.title(), c.description(), Date.valueOf(c.due()), c.status(), id);
        audit.record("HOMEWORK_UPDATED", "HOMEWORK", id, inst(), "Homework \"" + c.title() + "\" updated");
        return Map.of("id", id);
    }

    public Map<String, Object> homeworkStatus(Long id, String status) { return setStatus("school_homework", "HOMEWORK", id, status); }

    public void deleteHomework(Long id) {
        String t = existing("school_homework", id);
        jdbc.update("delete from school_homework where id = ?", id);
        audit.record("HOMEWORK_DELETED", "HOMEWORK", id, inst(), "Homework \"" + t + "\" deleted");
    }

    private String existing(String table, long id) {
        return jdbc.query("select title from " + table + " where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Item"));
    }
    private Map<String, Object> setStatus(String table, String type, Long id, String status) {
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!STATUS.contains(st)) throw ApiException.badRequest("Choose Draft or Published.");
        String t = existing(table, id);
        jdbc.update("update " + table + " set status = ?, updated_at = now() where id = ?", st, id);
        audit.record(type + (st.equals("PUBLISHED") ? "_PUBLISHED" : "_UNPUBLISHED"), type, id, inst(), "\"" + t + "\" " + (st.equals("PUBLISHED") ? "published" : "moved back to draft"));
        return Map.of("id", id, "status", st);
    }

    // ------------------------------------------------------------------ assignments

    private static final String AS_FROM = " from school_assignments h join sections sec on sec.id = h.section_id join school_classes c on c.id = sec.school_class_id join subjects s on s.id = h.subject_id";
    private static final String AS_COLS = "select h.id, h.title, h.description, h.section_id, sec.school_class_id, c.name, sec.name, h.subject_id, s.name, h.due_date, h.max_marks, h.status,"
            + " (select count(*) from school_assignment_submissions x join students st on st.id = x.student_id where x.assignment_id = h.id and st.section_id = h.section_id and st.active),"
            + " (select count(*) from school_assignment_submissions x join students st on st.id = x.student_id where x.assignment_id = h.id and st.section_id = h.section_id and st.active and x.marks is not null),"
            + " (select count(*) from students st where st.section_id = h.section_id and st.active)";
    private static AsgRow asg(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AsgRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9), day(rs.getDate(10)), rs.getInt(11), rs.getString(12), rs.getLong(13), rs.getLong(14), rs.getLong(15));
    }

    @Transactional(readOnly = true)
    public AsgPage assignments(Long classId, Long sectionId, Long subjectId, String status, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<Object> a = new ArrayList<>(List.of(inst()));
        String w = where(classId, sectionId, subjectId, status, "h", a);
        long total = base.count("select count(*)" + AS_FROM + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        return new AsgPage(jdbc.query(AS_COLS + AS_FROM + w + " order by h.due_date desc, h.id desc limit ? offset ?", (rs, n) -> asg(rs), a.toArray()), total, pg, sz);
    }

    private int marks(Integer m, Map<String, String> e) {
        int v = m == null ? 100 : m;
        if (v < 1 || v > 1000) e.put("maxMarks", "Enter 1 to 1000.");
        return v;
    }

    public Map<String, Object> createAssignment(AsgBody b) {
        Map<String, String> e = new LinkedHashMap<>();
        int mm = marks(b.maxMarks(), e);
        Clean c = check(b.sectionId(), b.subjectId(), b.title(), b.description(), b.dueDate(), b.status(), true, e);
        Long id = jdbc.queryForObject("insert into school_assignments (institution_id, section_id, subject_id, title, description, due_date, max_marks, status, created_by) values (?,?,?,?,?,?,?,?,?) returning id", Long.class,
                inst(), c.sectionId(), c.subjectId(), c.title(), c.description(), Date.valueOf(c.due()), mm, c.status(), current.principal().email());
        audit.record("ASSIGNMENT_CREATED", "ASSIGNMENT", id, inst(), "Assignment \"" + c.title() + "\" created as " + c.status().toLowerCase());
        return Map.of("id", id);
    }

    public Map<String, Object> updateAssignment(Long id, AsgBody b) {
        existing("school_assignments", id);
        Map<String, String> e = new LinkedHashMap<>();
        int mm = marks(b.maxMarks(), e);
        Clean c = check(b.sectionId(), b.subjectId(), b.title(), b.description(), b.dueDate(), b.status(), false, e);
        Double top = jdbc.queryForObject("select max(marks) from school_assignment_submissions where assignment_id = ?", Double.class, id);
        if (top != null && top > mm) throw new FieldErrorsException(Map.of("maxMarks", "Marks of " + top + " are already entered."));
        long old = base.count("select section_id from school_assignments where id = ?", id);
        if (old != c.sectionId() && base.count("select count(*) from school_assignment_submissions where assignment_id = ?", id) > 0) throw new FieldErrorsException(Map.of("sectionId", "Submissions are already recorded, so the class can't change."));
        jdbc.update("update school_assignments set section_id=?, subject_id=?, title=?, description=?, due_date=?, max_marks=?, status=?, updated_at=now() where id=?", c.sectionId(), c.subjectId(), c.title(), c.description(), Date.valueOf(c.due()), mm, c.status(), id);
        audit.record("ASSIGNMENT_UPDATED", "ASSIGNMENT", id, inst(), "Assignment \"" + c.title() + "\" updated");
        return Map.of("id", id);
    }

    public Map<String, Object> assignmentStatus(Long id, String status) { return setStatus("school_assignments", "ASSIGNMENT", id, status); }

    public void deleteAssignment(Long id) {
        String t = existing("school_assignments", id);
        jdbc.update("delete from school_assignments where id = ?", id);
        audit.record("ASSIGNMENT_DELETED", "ASSIGNMENT", id, inst(), "Assignment \"" + t + "\" deleted");
    }

    @Transactional(readOnly = true)
    public List<Submission> submissions(Long id) {
        existing("school_assignments", id);
        return jdbc.query("select st.id, st.admission_no, st.full_name, x.assignment_id is not null, x.submitted_at, x.marks, x.remarks from school_assignments a join students st on st.section_id = a.section_id and st.active left join school_assignment_submissions x on x.assignment_id = a.id and x.student_id = st.id where a.id = ? and a.institution_id = ? order by st.full_name, st.id",
                (rs, n) -> new Submission(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), iso(rs.getTimestamp(5)), rs.getObject(6) == null ? null : rs.getDouble(6), rs.getString(7)), id, inst());
    }

    public Map<String, Object> saveSubmission(Long id, Long studentId, SubBody b) {
        long i = inst();
        Object[] a = jdbc.query("select title, max_marks, section_id from school_assignments where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getInt(2), rs.getLong(3) }, id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Assignment"));
        if (base.count("select count(*) from students where id = ? and institution_id = ? and section_id = ? and active", studentId, i, a[2]) == 0) throw ApiException.notFound("Student in this class");
        if (b.submitted() == null) throw ApiException.badRequest("Say whether the work was submitted.");
        if (!b.submitted()) {
            jdbc.update("delete from school_assignment_submissions where assignment_id = ? and student_id = ?", id, studentId);
            return Map.of("submitted", false);
        }
        if (b.marks() != null && (b.marks() < 0 || b.marks() > (Integer) a[1] || Double.isNaN(b.marks()))) throw new FieldErrorsException(Map.of("marks", "Marks must be between 0 and " + a[1] + "."));
        jdbc.update("insert into school_assignment_submissions (assignment_id, student_id, institution_id, marks, remarks) values (?,?,?,?,?) on conflict (assignment_id, student_id) do update set marks = excluded.marks, remarks = excluded.remarks",
                id, studentId, i, b.marks(), AcademicsService.clip(b.remarks(), 300));
        return Map.of("submitted", true);
    }
}
