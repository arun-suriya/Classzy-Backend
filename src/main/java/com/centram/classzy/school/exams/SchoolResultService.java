package com.centram.classzy.school.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.comm.NotifyService;
import com.centram.classzy.school.exams.ExamDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks entry (draft -> submitted -> approved) and the reports built from approved results.
 * Percentages and grades are worked out when read, using the grade bands as they are now.
 */
@Service
@Transactional
public class SchoolResultService {

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;
    private final SchoolExamService exams;
    private final NotifyService notify;

    public SchoolResultService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, SchoolExamService exams, NotifyService notify) { this.jdbc = jdbc; this.current = current; this.audit = audit; this.exams = exams; this.notify = notify; }

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }

    private record Ctx(ExamRow exam, long classId, String sectionLabel, String subjectName, double max) {}

    private Ctx context(Long examId, Long subjectId, Long sectionId) {
        ExamRow x = exams.get(examId);
        if (!x.published()) throw ApiException.conflict("Publish the exam schedule before entering results.");
        if (subjectId == null || sectionId == null) throw ApiException.badRequest("Choose a subject and a grade / section.");
        if (!x.sectionIds().contains(sectionId)) throw ApiException.badRequest("That section isn't part of this exam.");
        Object[] sec = jdbc.query("select s.school_class_id, c.name || ' – ' || s.name from sections s join school_classes c on c.id = s.school_class_id where s.id = ? and s.institution_id = ?", (rs, n) -> new Object[] { rs.getLong(1), rs.getString(2) }, sectionId, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Section"));
        long classId = (Long) sec[0];
        String subject = jdbc.query("select su.name from class_subjects cs join subjects su on su.id = cs.subject_id where cs.class_id = ? and cs.subject_id = ?", (rs, n) -> rs.getString(1), classId, subjectId).stream().findFirst().orElseThrow(() -> ApiException.badRequest("That subject isn't taught in this grade."));
        double max = jdbc.query("select max_marks from school_exam_papers where exam_id = ? and class_id = ? and subject_id = ?", (rs, n) -> rs.getBigDecimal(1).doubleValue(), examId, classId, subjectId).stream().findFirst().orElse(100.0);
        return new Ctx(x, classId, (String) sec[1], subject, max);
    }

    private String status(Long examId, Long subjectId, Long sectionId) {
        return jdbc.query("select status from school_exam_results where exam_id = ? and subject_id = ? and section_id = ?", (rs, n) -> rs.getString(1), examId, subjectId, sectionId).stream().findFirst().orElse("DRAFT");
    }

    @Transactional(readOnly = true)
    public Entry entry(Long examId, Long subjectId, Long sectionId) {
        Ctx c = context(examId, subjectId, sectionId);
        String st = status(examId, subjectId, sectionId);
        String by = jdbc.query("select reviewed_by from school_exam_results where exam_id = ? and subject_id = ? and section_id = ?", (rs, n) -> rs.getString(1), examId, subjectId, sectionId).stream().findFirst().orElse(null);
        List<EntryRow> rows = jdbc.query("select s.id, s.admission_no, s.full_name, m.marks, coalesce(m.absent, false) from students s left join school_exam_marks m on m.student_id = s.id and m.exam_id = ? and m.subject_id = ? where s.institution_id = ? and s.section_id = ? and s.active order by s.admission_no, s.id",
                (rs, n) -> new EntryRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4) == null ? null : rs.getBigDecimal(4).doubleValue(), rs.getBoolean(5)), examId, subjectId, inst(), sectionId);
        return new Entry(st, c.max(), c.exam().passPercent(), st.equals("DRAFT"), by, rows);
    }

    public Entry save(Long examId, EntryBody b) {
        Ctx c = context(examId, b.subjectId(), b.sectionId());
        if (!status(examId, b.subjectId(), b.sectionId()).equals("DRAFT")) throw ApiException.conflict("These results are submitted. Return them to draft to edit.");
        Map<Long, Boolean> valid = new LinkedHashMap<>();
        jdbc.query("select id from students where institution_id = ? and section_id = ? and active", (rs) -> { valid.put(rs.getLong(1), true); }, inst(), b.sectionId());
        Map<String, String> errs = new LinkedHashMap<>();
        int k = 0;
        for (EntryRowBody r : b.rows() == null ? List.<EntryRowBody>of() : b.rows()) {
            String key = "row" + r.studentId();
            if (r.studentId() == null || !valid.containsKey(r.studentId())) throw ApiException.badRequest("A student in the list isn't in this section.");
            boolean abs = Boolean.TRUE.equals(r.absent());
            if (abs && r.marks() != null) errs.put(key, "A student can't be absent and have marks.");
            else if (r.marks() != null) {
                double m = r.marks();
                if (Double.isNaN(m) || m < 0 || m > c.max()) errs.put(key, "Marks must be between 0 and " + trim(c.max()) + ".");
                else if (BigDecimal.valueOf(m).scale() > 2) errs.put(key, "Use at most 2 decimal places.");
            }
            k++;
        }
        if (!errs.isEmpty()) throw new FieldErrorsException(errs);
        jdbc.update("insert into school_exam_results (exam_id, subject_id, section_id) values (?,?,?) on conflict do nothing", examId, b.subjectId(), b.sectionId());
        for (EntryRowBody r : b.rows() == null ? List.<EntryRowBody>of() : b.rows()) {
            boolean abs = Boolean.TRUE.equals(r.absent());
            if (!abs && r.marks() == null) { jdbc.update("delete from school_exam_marks where exam_id = ? and subject_id = ? and student_id = ?", examId, b.subjectId(), r.studentId()); continue; }
            jdbc.update("insert into school_exam_marks (exam_id, subject_id, student_id, marks, absent) values (?,?,?,?,?) on conflict (exam_id, subject_id, student_id) do update set marks = excluded.marks, absent = excluded.absent",
                    examId, b.subjectId(), r.studentId(), abs ? null : BigDecimal.valueOf(r.marks()).setScale(2, RoundingMode.HALF_UP), abs);
        }
        audit.record("EXAM_MARKS_SAVED", "EXAM", examId, inst(), "Draft marks saved: " + c.subjectName() + ", " + c.sectionLabel() + " (" + c.exam().name() + ")");
        return entry(examId, b.subjectId(), b.sectionId());
    }

    private static String trim(double d) { return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString(); }

    public Entry submit(Long examId, Ref r) {
        Ctx c = context(examId, r.subjectId(), r.sectionId());
        if (!status(examId, r.subjectId(), r.sectionId()).equals("DRAFT")) throw ApiException.conflict("These results were already submitted.");
        Long total = jdbc.queryForObject("select count(*) from students where institution_id = ? and section_id = ? and active", Long.class, inst(), r.sectionId());
        Long done = jdbc.queryForObject("select count(*) from school_exam_marks m join students s on s.id = m.student_id where m.exam_id = ? and m.subject_id = ? and s.section_id = ? and s.active", Long.class, examId, r.subjectId(), r.sectionId());
        if (total == null || total == 0) throw ApiException.conflict("There are no active students in this section.");
        if (done == null || done < total) throw ApiException.conflict((total - (done == null ? 0 : done)) + " student(s) still have no marks. Enter marks or mark them absent, then save before submitting.");
        jdbc.update("insert into school_exam_results (exam_id, subject_id, section_id, status, submitted_by, submitted_at) values (?,?,?, 'SUBMITTED', ?, now()) on conflict (exam_id, subject_id, section_id) do update set status = 'SUBMITTED', submitted_by = excluded.submitted_by, submitted_at = now(), reviewed_by = null, reviewed_at = null",
                examId, r.subjectId(), r.sectionId(), actor());
        audit.record("EXAM_RESULTS_SUBMITTED", "EXAM", examId, inst(), "Results submitted for review: " + c.subjectName() + ", " + c.sectionLabel() + " (" + c.exam().name() + ")");
        notify.add(inst(), "ACADEMICS", "Results awaiting review", c.exam().name() + " – " + c.subjectName() + ", " + c.sectionLabel() + " submitted for review.", "/school/examinations");
        return entry(examId, r.subjectId(), r.sectionId());
    }

    public Entry approve(Long examId, Ref r) {
        Ctx c = context(examId, r.subjectId(), r.sectionId());
        if (!status(examId, r.subjectId(), r.sectionId()).equals("SUBMITTED")) throw ApiException.conflict("Only submitted results can be approved.");
        jdbc.update("update school_exam_results set status = 'APPROVED', reviewed_by = ?, reviewed_at = now() where exam_id = ? and subject_id = ? and section_id = ?", actor(), examId, r.subjectId(), r.sectionId());
        audit.record("EXAM_RESULTS_APPROVED", "EXAM", examId, inst(), "Results approved: " + c.subjectName() + ", " + c.sectionLabel() + " (" + c.exam().name() + ")");
        return entry(examId, r.subjectId(), r.sectionId());
    }

    public Entry reopen(Long examId, Ref r) {
        Ctx c = context(examId, r.subjectId(), r.sectionId());
        if (status(examId, r.subjectId(), r.sectionId()).equals("DRAFT")) throw ApiException.conflict("These results are already in draft.");
        jdbc.update("update school_exam_results set status = 'DRAFT', reviewed_by = null, reviewed_at = null where exam_id = ? and subject_id = ? and section_id = ?", examId, r.subjectId(), r.sectionId());
        audit.record("EXAM_RESULTS_REOPENED", "EXAM", examId, inst(), "Results returned to draft: " + c.subjectName() + ", " + c.sectionLabel() + " (" + c.exam().name() + ")");
        return entry(examId, r.subjectId(), r.sectionId());
    }

    // ------------------------------------------------------------------ reports

    private record Mark(long classId, String className, long sectionId, String sectionName, long studentId, String adm, String student, long subjectId, String subject, Double marks, boolean absent, double max) {}

    private List<Mark> approved(Long examId, Long classId) {
        List<Object> a = new ArrayList<>(List.of(examId, inst()));
        String w = classId == null ? "" : " and c.id = ?";
        if (classId != null) a.add(classId);
        return jdbc.query("select c.id, c.name, sec.id, sec.name, st.id, st.admission_no, st.full_name, su.id, su.name, m.marks, m.absent, coalesce(p.max_marks, 100)"
                + " from school_exam_marks m join school_exam_results r on r.exam_id = m.exam_id and r.subject_id = m.subject_id and r.status = 'APPROVED'"
                + " join students st on st.id = m.student_id and st.section_id = r.section_id join sections sec on sec.id = st.section_id join school_classes c on c.id = sec.school_class_id join subjects su on su.id = m.subject_id"
                + " left join school_exam_papers p on p.exam_id = m.exam_id and p.class_id = c.id and p.subject_id = m.subject_id"
                + " where m.exam_id = ? and st.institution_id = ?" + w + " order by coalesce(c.grade_level, -1), c.name, sec.name, st.admission_no, su.name",
                (rs, n) -> new Mark(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9), rs.getBigDecimal(10) == null ? null : rs.getBigDecimal(10).doubleValue(), rs.getBoolean(11), rs.getBigDecimal(12).doubleValue()), a.toArray());
    }

    private static String pct1(double v) { return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString(); }
    private static double ratio(double a, double b) { return b == 0 ? 0 : a * 100.0 / b; }

    private static String gradeOf(List<Band> bands, double pct) {
        int r = (int) Math.round(pct);
        return bands.stream().filter((b) -> r >= b.min() && r <= b.max()).findFirst().map(Band::grade).orElse("—");
    }

    private record Stu(long id, String adm, String name, long classId, String className, String sectionLabel, Map<Long, Mark> bySubject) {}

    private Map<Long, Stu> students(List<Mark> marks) {
        Map<Long, Stu> out = new LinkedHashMap<>();
        for (Mark m : marks) out.computeIfAbsent(m.studentId(), (k) -> new Stu(m.studentId(), m.adm(), m.student(), m.classId(), m.className(), m.className() + " – " + m.sectionName(), new LinkedHashMap<>())).bySubject().put(m.subjectId(), m);
        return out;
    }

    private static boolean passed(Stu s, double pass) { return s.bySubject().values().stream().noneMatch((m) -> m.absent() || m.marks() == null || ratio(m.marks(), m.max()) < pass); }
    private static double overall(Stu s) { double got = 0, max = 0; for (Mark m : s.bySubject().values()) if (!m.absent() && m.marks() != null) { got += m.marks(); max += m.max(); } return ratio(got, max); }
    private static boolean hasScore(Stu s) { return s.bySubject().values().stream().anyMatch((m) -> !m.absent() && m.marks() != null); }

    @Transactional(readOnly = true)
    public Table report(Long examId, String type, Long classId) {
        ExamRow x = exams.get(examId);
        List<Band> bands = exams.bands();
        List<Mark> marks = approved(examId, classId);
        String grade = classId == null ? "All grades" : jdbc.query("select name from school_classes where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), classId, inst()).stream().findFirst().orElse("");
        String sub = x.name() + " · " + grade + " · Pass mark " + trim(x.passPercent()) + "%";
        String note = "Counts approved results only. A student who is absent in any subject is counted as not passed. Grades come from the current grade bands.";
        double pass = x.passPercent();
        switch (type == null ? "" : type) {
            case "RESULT_SUMMARY" -> {
                Map<Long, Stu> st = students(marks);
                Map<String, int[]> by = new TreeMap<>(); // label -> students, passed
                Map<String, Double> sum = new TreeMap<>();
                Map<String, Integer> cnt = new TreeMap<>();
                for (Stu s : st.values()) { by.computeIfAbsent(s.sectionLabel(), (k) -> new int[2])[0]++; if (passed(s, pass)) by.get(s.sectionLabel())[1]++; if (hasScore(s)) { sum.merge(s.sectionLabel(), overall(s), Double::sum); cnt.merge(s.sectionLabel(), 1, Integer::sum); } }
                List<List<String>> rows = new ArrayList<>();
                int ts = 0, tp = 0; double tsum = 0; int tc = 0;
                for (var e : by.entrySet()) { int[] v = e.getValue(); ts += v[0]; tp += v[1]; tsum += sum.getOrDefault(e.getKey(), 0.0); tc += cnt.getOrDefault(e.getKey(), 0);
                    rows.add(List.of(e.getKey(), String.valueOf(v[0]), String.valueOf(v[1]), String.valueOf(v[0] - v[1]), pct1(ratio(v[1], v[0])), cnt.containsKey(e.getKey()) ? pct1(sum.get(e.getKey()) / cnt.get(e.getKey())) : "—")); }
                List<String> total = rows.isEmpty() ? null : List.of("Total", String.valueOf(ts), String.valueOf(tp), String.valueOf(ts - tp), pct1(ratio(tp, ts)), tc == 0 ? "—" : pct1(tsum / tc));
                return new Table("Result Summary", sub, List.of("Grade / Section", "Students", "Passed", "Not passed", "Pass %", "Average %"), List.of(false, true, true, true, true, true), rows, total, note);
            }
            case "SUBJECT_PERFORMANCE" -> {
                Map<String, List<Mark>> by = new LinkedHashMap<>();
                for (Mark m : marks) by.computeIfAbsent(m.className() + "\u0000" + m.subject(), (k) -> new ArrayList<>()).add(m);
                List<List<String>> rows = new ArrayList<>();
                for (var e : by.entrySet()) {
                    List<Mark> ms = e.getValue();
                    List<Double> p = ms.stream().filter((m) -> !m.absent() && m.marks() != null).map((m) -> ratio(m.marks(), m.max())).toList();
                    String[] k = e.getKey().split("\u0000");
                    long ok = p.stream().filter((v) -> v >= pass).count();
                    rows.add(List.of(k[0], k[1], String.valueOf(p.size()), String.valueOf(ms.size() - p.size()), p.isEmpty() ? "—" : pct1(p.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                            p.isEmpty() ? "—" : pct1(p.stream().mapToDouble(Double::doubleValue).max().orElse(0)), p.isEmpty() ? "—" : pct1(p.stream().mapToDouble(Double::doubleValue).min().orElse(0)), p.isEmpty() ? "—" : pct1(ratio(ok, p.size()))));
                }
                return new Table("Subject Performance", sub, List.of("Grade", "Subject", "Appeared", "Absent", "Average %", "Highest %", "Lowest %", "Pass %"), List.of(false, false, true, true, true, true, true, true), rows, null, note);
            }
            case "REPORT_CARDS" -> {
                Map<Long, Stu> st = students(marks);
                Map<Long, String> names = new LinkedHashMap<>();
                for (Mark m : marks) names.put(m.subjectId(), m.subject());
                Map<Long, String> subjects = new LinkedHashMap<>();
                names.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach((e) -> subjects.put(e.getKey(), e.getValue()));
                Map<Long, Integer> rank = new LinkedHashMap<>();
                Map<Long, List<Stu>> byClass = new LinkedHashMap<>();
                for (Stu s : st.values()) byClass.computeIfAbsent(s.classId(), (k) -> new ArrayList<>()).add(s);
                for (List<Stu> l : byClass.values()) { List<Stu> sorted = l.stream().sorted(Comparator.comparingDouble((Stu s) -> overall(s)).reversed()).toList(); int pos = 0; double last = -1; int r = 0; for (Stu s : sorted) { pos++; double v = Math.round(overall(s) * 100) / 100.0; if (v != last) { r = pos; last = v; } rank.put(s.id(), r); } }
                List<String> cols = new ArrayList<>(List.of("Admission No", "Student", "Grade / Section"));
                subjects.values().forEach(cols::add);
                cols.addAll(List.of("Total", "Percentage", "Grade", "Result", "Rank"));
                List<Boolean> num = new ArrayList<>();
                for (int k = 0; k < cols.size(); k++) num.add(k >= 3);
                List<List<String>> rows = new ArrayList<>();
                for (Stu s : st.values()) {
                    List<String> r = new ArrayList<>(List.of(s.adm(), s.name(), s.sectionLabel()));
                    double got = 0, max = 0;
                    for (Long sid : subjects.keySet()) { Mark m = s.bySubject().get(sid); if (m == null) r.add("—"); else if (m.absent() || m.marks() == null) r.add("AB"); else { r.add(trim(m.marks())); got += m.marks(); max += m.max(); } }
                    double p = ratio(got, max);
                    r.add(trim(got) + " / " + trim(max)); r.add(max == 0 ? "—" : pct1(p)); r.add(max == 0 ? "—" : gradeOf(bands, p)); r.add(passed(s, pass) ? "Pass" : "Not passed"); r.add(hasScore(s) ? String.valueOf(rank.get(s.id())) : "—");
                    rows.add(r);
                }
                return new Table("Student Report Cards", sub, cols, num, rows, null, note + " AB = absent; — = no approved result for that subject.");
            }
            default -> throw ApiException.badRequest("Unknown report.");
        }
    }
}
