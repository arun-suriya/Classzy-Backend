package com.centram.classzy.school.academics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.academics.AcaDto.*;
import com.centram.classzy.school.admissions.AdmDto.ClassOpt;
import com.centram.classzy.school.admissions.AdmDto.Year;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Subjects per grade and classes (sections) for one school. Every query is limited to CurrentUser.institutionId(). */
@Service
@Transactional
public class AcademicsService {

    static final List<String> CATEGORIES = List.of("Language", "Core", "Skill", "Co-Curricular");

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public AcademicsService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) {
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
    }

    long inst() { return current.institutionId(); }
    long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    static String clip(String v, int max) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t); }

    private static final String SEC_SQL = "select s.id, s.school_class_id, c.name, c.academic_year_id, s.name, s.teacher_id, t.full_name, s.room, s.max_students, (select count(*) from students st where st.section_id = s.id and st.active), s.active"
            + " from sections s join school_classes c on c.id = s.school_class_id left join teachers t on t.id = s.teacher_id where s.institution_id = ?";
    static Sec sec(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Sec(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getObject(6) == null ? null : rs.getLong(6), rs.getString(7), rs.getString(8), rs.getObject(9) == null ? null : rs.getInt(9), rs.getLong(10), rs.getBoolean(11));
    }
    Sec section(long id) {
        return jdbc.query(SEC_SQL + " and s.id = ?", (rs, n) -> sec(rs), inst(), id).stream().findFirst().orElseThrow(() -> ApiException.notFound("Class"));
    }

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        List<Year> years = jdbc.query("select id, name, is_current from academic_years where institution_id = ? and active order by start_date desc", (rs, n) -> new Year(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), i);
        List<ClassOpt> classes = jdbc.query("select id, name, academic_year_id from school_classes where institution_id = ? and active order by coalesce(grade_level, -1), name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getString(2), rs.getLong(3)), i);
        List<Sec> sections = jdbc.query(SEC_SQL + " and s.active order by s.name", (rs, n) -> sec(rs), i);
        List<SubjectOpt> subjects = jdbc.query("select id, academic_year_id, name, code, category from subjects where institution_id = ? and active order by name", (rs, n) -> new SubjectOpt(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)), i);
        List<ClassSubject> cs = jdbc.query("select cs.class_id, cs.subject_id, s.code, s.name, s.category, cs.periods_per_week from class_subjects cs join subjects s on s.id = cs.subject_id where cs.institution_id = ? order by s.name", (rs, n) -> new ClassSubject(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6)), i);
        List<Teacher> teachers = jdbc.query("select id, full_name from teachers where institution_id = ? and active order by full_name", (rs, n) -> new Teacher(rs.getLong(1), rs.getString(2)), i);
        return new Options(years, classes, sections, subjects, cs, teachers, CATEGORIES);
    }

    // ------------------------------------------------------------------ subjects of a grade

    private Object[] classRow(Long classId) {
        if (classId == null) throw new FieldErrorsException(Map.of("classId", "Choose a grade."));
        return jdbc.query("select id, academic_year_id, grade_level, name from school_classes where id = ? and institution_id = ?", (rs, n) -> new Object[] { rs.getLong(1), rs.getLong(2), rs.getObject(3) == null ? null : rs.getInt(3), rs.getString(4) }, classId, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Grade"));
    }

    public Map<String, Object> addSubject(SubjectBody b) {
        long i = inst();
        Object[] c = classRow(b.classId());
        Map<String, String> e = new LinkedHashMap<>();
        int periods = b.periods() == null ? 0 : b.periods();
        if (periods < 1 || periods > 20) e.put("periods", "Enter 1 to 20 periods per week.");
        Long sid = b.subjectId();
        if (sid != null) {
            if (count("select count(*) from subjects where id = ? and institution_id = ? and academic_year_id = ? and active", sid, i, c[1]) == 0) e.put("subjectId", "Choose a subject of this academic year.");
        } else {
            String name = clip(b.name(), 100), code = clip(b.code(), 20);
            if (name == null) e.put("name", "Required.");
            if (code == null) e.put("code", "Required.");
            if (b.category() == null || !CATEGORIES.contains(b.category())) e.put("category", "Choose a type.");
            if (code != null && count("select count(*) from subjects where academic_year_id = ? and lower(code) = lower(?)", c[1], code) > 0) e.put("code", "That code is already used this year.");
            if (e.isEmpty()) {
                sid = jdbc.queryForObject("insert into subjects (institution_id, academic_year_id, name, code, category) values (?,?,?,?,?) returning id", Long.class, i, c[1], name, code.toUpperCase(), b.category());
                if (c[2] != null) jdbc.update("insert into subject_grade_levels (subject_id, grade_level) values (?, ?) on conflict do nothing", sid, c[2]);
            }
        }
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        if (count("select count(*) from class_subjects where class_id = ? and subject_id = ?", c[0], sid) > 0) throw new FieldErrorsException(Map.of("subjectId", "This subject is already added to " + c[3] + "."));
        jdbc.update("insert into class_subjects (institution_id, class_id, subject_id, periods_per_week) values (?,?,?,?)", i, c[0], sid, periods);
        audit.record("ACADEMICS_SUBJECT_ADDED", "SUBJECT", sid, i, "Subject added to " + c[3] + " with " + periods + " periods a week");
        return Map.of("subjectId", sid);
    }

    public void setPeriods(Long classId, Long subjectId, Integer periods) {
        if (periods == null || periods < 1 || periods > 20) throw new FieldErrorsException(Map.of("periods", "Enter 1 to 20 periods per week."));
        if (jdbc.update("update class_subjects set periods_per_week = ? where class_id = ? and subject_id = ? and institution_id = ?", periods, classId, subjectId, inst()) == 0) throw ApiException.notFound("Subject");
        audit.record("ACADEMICS_PERIODS_UPDATED", "SUBJECT", subjectId, inst(), "Periods per week set to " + periods);
    }

    public void removeSubject(Long classId, Long subjectId) {
        long i = inst();
        if (count("select count(*) from class_subjects where class_id = ? and subject_id = ? and institution_id = ?", classId, subjectId, i) == 0) throw ApiException.notFound("Subject");
        if (count("select count(*) from timetable_entries te join sections s on s.id = te.section_id where s.school_class_id = ? and te.subject_id = ?", classId, subjectId) > 0) throw ApiException.conflict("This subject is on the timetable. Clear those periods first.");
        if (count("select count(*) from school_homework h join sections s on s.id = h.section_id where s.school_class_id = ? and h.subject_id = ?", classId, subjectId) + count("select count(*) from school_assignments h join sections s on s.id = h.section_id where s.school_class_id = ? and h.subject_id = ?", classId, subjectId) > 0)
            throw ApiException.conflict("This subject has homework or assignments for this grade, so it can't be removed.");
        jdbc.update("delete from class_subjects where class_id = ? and subject_id = ?", classId, subjectId);
        audit.record("ACADEMICS_SUBJECT_REMOVED", "SUBJECT", subjectId, i, "Subject removed from grade");
    }

    // ------------------------------------------------------------------ classes (sections)

    @Transactional(readOnly = true)
    public List<Sec> sections(Long yearId, Long classId) {
        StringBuilder q = new StringBuilder(SEC_SQL);
        java.util.List<Object> a = new java.util.ArrayList<>(List.of(inst()));
        if (yearId != null) { q.append(" and c.academic_year_id = ?"); a.add(yearId); }
        if (classId != null) { q.append(" and s.school_class_id = ?"); a.add(classId); }
        q.append(" order by coalesce(c.grade_level, -1), c.name, s.name");
        return jdbc.query(q.toString(), (rs, n) -> sec(rs), a.toArray());
    }

    private void checkSec(SecBody b, Map<String, String> e) {
        if (clip(b.name(), 40) == null) e.put("name", "Required.");
        if (b.teacherId() != null && count("select count(*) from teachers where id = ? and institution_id = ? and active", b.teacherId(), inst()) == 0) e.put("teacherId", "Choose a teacher from the list.");
        if (b.maxStudents() != null && (b.maxStudents() < 1 || b.maxStudents() > 500)) e.put("maxStudents", "Enter 1 to 500.");
    }

    public Map<String, Object> addSection(SecBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        checkSec(b, e);
        Long classId = b.classId();
        String grade = clip(b.newGrade(), 60);
        if (classId == null && grade == null) e.put("classId", "Choose a grade or enter a new one.");
        if (classId == null && grade != null && b.yearId() == null) e.put("yearId", "Choose the academic year.");
        if (classId != null && count("select count(*) from school_classes where id = ? and institution_id = ?", classId, i) == 0) e.put("classId", "Choose a grade from the list.");
        if (classId == null && grade != null && b.yearId() != null && count("select count(*) from academic_years where id = ? and institution_id = ?", b.yearId(), i) == 0) e.put("yearId", "Choose an academic year.");
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        if (classId == null) {
            if (count("select count(*) from school_classes where academic_year_id = ? and lower(name) = lower(?)", b.yearId(), grade) > 0) throw new FieldErrorsException(Map.of("newGrade", "That grade already exists. Pick it from the list."));
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})\\s*$").matcher(grade);
            Integer level = m.find() ? Integer.valueOf(m.group(1)) : null;
            classId = jdbc.queryForObject("insert into school_classes (institution_id, academic_year_id, name, grade_level) values (?,?,?,?) returning id", Long.class, i, b.yearId(), grade, level);
        }
        String name = clip(b.name(), 40);
        if (count("select count(*) from sections where school_class_id = ? and lower(name) = lower(?)", classId, name) > 0) throw new FieldErrorsException(Map.of("name", "That section already exists in this grade."));
        Long id = jdbc.queryForObject("insert into sections (institution_id, school_class_id, name, teacher_id, room, max_students) values (?,?,?,?,?,?) returning id", Long.class, i, classId, name, b.teacherId(), clip(b.room(), 60), b.maxStudents());
        audit.record("ACADEMICS_CLASS_ADDED", "SECTION", id, i, "Class section " + name + " added");
        return Map.of("id", id);
    }

    public Map<String, Object> updateSection(Long id, SecBody b) {
        long i = inst();
        Sec cur = section(id);
        Map<String, String> e = new LinkedHashMap<>();
        checkSec(b, e);
        String name = clip(b.name(), 40);
        if (name != null && count("select count(*) from sections where school_class_id = ? and lower(name) = lower(?) and id <> ?", cur.classId(), name, id) > 0) e.put("name", "That section already exists in this grade.");
        if (b.maxStudents() != null && b.maxStudents() < cur.students()) e.put("maxStudents", cur.students() + " students are already in this section.");
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        jdbc.update("update sections set name = ?, teacher_id = ?, room = ?, max_students = ? where id = ?", name, b.teacherId(), clip(b.room(), 60), b.maxStudents(), id);
        audit.record("ACADEMICS_CLASS_UPDATED", "SECTION", id, i, "Class section " + name + " updated");
        return Map.of("id", id);
    }

    public void deleteSection(Long id) {
        long i = inst();
        Sec s = section(id);
        if (count("select count(*) from students where section_id = ?", id) > 0) throw ApiException.conflict("Section " + s.name() + " still has students. Move them first.");
        if (count("select count(*) from school_homework where section_id = ?", id) + count("select count(*) from school_assignments where section_id = ?", id) > 0) throw ApiException.conflict("Section " + s.name() + " has homework or assignments, so it can't be deleted.");
        jdbc.update("delete from sections where id = ?", id);
        audit.record("ACADEMICS_CLASS_DELETED", "SECTION", id, i, "Class section " + s.grade() + " " + s.name() + " deleted");
    }
}
