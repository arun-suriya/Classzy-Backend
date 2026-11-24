package com.centram.classzy.school.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.reports.RptDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only reports over the school's own records. Every query is scoped to the caller's school.
 * Attendance and exam results can't be reported yet because those modules have no data; assignment marks stand in for academic results.
 */
@Service
@Transactional(readOnly = true)
public class RptService {

    private static final List<Report> REPORTS = List.of(
        new Report("STU_ENROLL_CLASS", "Student", "Student Enrollment", "Class-wise Enrollment", true, null),
        new Report("STU_ENROLL_STATUS", "Student", "Student Enrollment", "Active and Inactive by Grade", true, null),
        new Report("STU_ATTENDANCE", "Student", "Attendance Summary", "Monthly Attendance", false, "Attendance isn't recorded in Classzy yet."),
        new Report("ADM_STATUS", "Admission", "Applications by Status", "Application Status Summary", true, null),
        new Report("ADM_CONVERSION", "Admission", "Admission Conversion", "Application to Admission Rate", true, null),
        new Report("FEE_TERM", "Fee", "Fee Collection", "Term-wise Collection", true, null),
        new Report("FEE_OUTSTANDING", "Fee", "Outstanding Fees", "Pending Fees by Grade", true, null),
        new Report("ACA_GRADE", "Academic", "Academic Performance", "Grade-wise Assignment Results", true, null),
        new Report("ACA_SUBJECT", "Academic", "Subject Performance", "Subject-wise Analysis", true, null));

    private record Col(String key, String label, String expr, boolean agg, boolean numeric) {}
    private record Src(String code, String label, String from, String instCol, String yearCol, String classCol, String sectionCol, List<Col> cols) {}

    private static final List<Src> SOURCES = List.of(
        new Src("STUDENTS", "Students", "students s left join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id left join academic_years y on y.id = c.academic_year_id",
            "s.institution_id", "c.academic_year_id", "s.school_class_id", "s.section_id", List.of(
                new Col("year", "Academic Year", "y.name", false, false), new Col("grade", "Grade", "c.name", false, false), new Col("section", "Section", "sec.name", false, false),
                new Col("status", "Status", "case when s.active then 'Active' else 'Inactive' end", false, false), new Col("admissionNo", "Admission No", "s.admission_no", false, false),
                new Col("name", "Student", "s.full_name", false, false), new Col("guardian", "Guardian", "s.guardian_name", false, false), new Col("guardianPhone", "Guardian Phone", "s.guardian_phone", false, false),
                new Col("enrolled", "Enrolled", "count(*)", true, true))),
        new Src("APPLICATIONS", "Applications", "school_applications a left join school_classes c on c.id = a.class_id left join academic_years y on y.id = a.academic_year_id",
            "a.institution_id", "a.academic_year_id", "a.class_id", null, List.of(
                new Col("year", "Intake Year", "y.name", false, false), new Col("grade", "Grade", "coalesce(c.name, a.grade)", false, false), new Col("stage", "Status", "replace(initcap(lower(a.stage)), '_', ' ')", false, false),
                new Col("applicationNo", "Application No", "a.application_no", false, false), new Col("applicant", "Applicant", "a.student_name", false, false), new Col("guardian", "Guardian", "a.guardian_name", false, false),
                new Col("phone", "Phone", "a.phone", false, false), new Col("submitted", "Submitted On", "to_char(a.submitted_at at time zone 'Asia/Kolkata', 'DD Mon YYYY')", false, false),
                new Col("applications", "Applications", "count(*)", true, true))),
        new Src("FEES", "Fee invoices", "school_invoice_v v join students s on s.id = v.student_id left join school_classes c on c.id = s.school_class_id left join terms t on t.id = v.term_id left join academic_years y on y.id = v.academic_year_id",
            "v.institution_id", "v.academic_year_id", "s.school_class_id", "s.section_id", List.of(
                new Col("year", "Academic Year", "y.name", false, false), new Col("term", "Term", "coalesce(t.name, 'No term')", false, false), new Col("grade", "Grade", "c.name", false, false),
                new Col("student", "Student", "s.full_name", false, false), new Col("invoiceNo", "Invoice No", "v.invoice_no", false, false), new Col("feeType", "Fee Type", "v.fee_type", false, false),
                new Col("net", "Payable (₹)", "v.net", false, true), new Col("paid", "Paid (₹)", "v.paid", false, true), new Col("balance", "Balance (₹)", "v.balance", false, true),
                new Col("invoices", "Invoices", "count(*)", true, true), new Col("totalNet", "Total Payable (₹)", "sum(v.net)", true, true),
                new Col("totalPaid", "Total Paid (₹)", "sum(v.paid)", true, true), new Col("totalBalance", "Total Balance (₹)", "sum(v.balance)", true, true))));

    static final int MAX_ROWS = 5000;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public RptService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) { this.jdbc = jdbc; this.current = current; this.audit = audit; }

    private long inst() { return current.institutionId(); }

    public Options options() {
        long i = inst();
        List<Year> years = jdbc.query("select id, name, is_current from academic_years where institution_id = ? order by start_date desc", (rs, n) -> new Year(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), i);
        List<ClassOpt> classes = jdbc.query("select id, academic_year_id, name from school_classes where institution_id = ? and active order by coalesce(grade_level, -1), name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i);
        List<SecOpt> secs = jdbc.query("select id, school_class_id, name from sections where institution_id = ? and active order by name", (rs, n) -> new SecOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i);
        List<TermOpt> terms = jdbc.query("select id, academic_year_id, name from terms where institution_id = ? and active order by start_date", (rs, n) -> new TermOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i);
        List<SubjectOpt> subs = jdbc.query("select id, academic_year_id, name from subjects where institution_id = ? and active order by name", (rs, n) -> new SubjectOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i);
        List<Source> src = SOURCES.stream().map((s) -> new Source(s.code(), s.label(), s.cols().stream().map((c) -> new SourceCol(c.key(), c.label(), c.agg())).toList())).toList();
        return new Options(years, classes, secs, terms, subs, REPORTS, src);
    }

    // ------------------------------------------------------------------ helpers

    private static String fmt(Object o) {
        if (o == null) return "—";
        if (o instanceof BigDecimal b) return b.signum() == 0 ? "0" : b.scale() <= 0 ? b.toPlainString() : b.setScale(2, RoundingMode.HALF_UP).toPlainString();
        if (o instanceof Double || o instanceof Float) return BigDecimal.valueOf(((Number) o).doubleValue()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        return o.toString();
    }

    private record Raw(List<String> heads, List<List<String>> rows, boolean truncated, List<Boolean> numeric) {}

    private Raw query(String sql, List<Object> args, int limit) {
        List<List<String>> rows = new ArrayList<>();
        List<String> heads = new ArrayList<>();
        List<Boolean> num = new ArrayList<>();
        boolean[] trunc = { false };
        jdbc.query(sql + " limit " + (limit + 1), (rs) -> {
            if (heads.isEmpty()) { ResultSetMetaData m = rs.getMetaData(); for (int k = 1; k <= m.getColumnCount(); k++) { heads.add(m.getColumnLabel(k)); int t = m.getColumnType(k); num.add(t == java.sql.Types.BIGINT || t == java.sql.Types.INTEGER || t == java.sql.Types.NUMERIC || t == java.sql.Types.DECIMAL || t == java.sql.Types.DOUBLE || t == java.sql.Types.SMALLINT); } }
            if (rows.size() >= limit) { trunc[0] = true; return; }
            List<String> r = new ArrayList<>();
            for (int k = 1; k <= heads.size(); k++) r.add(fmt(rs.getObject(k)));
            rows.add(r);
        }, args.toArray());
        if (heads.isEmpty()) { /* no rows: columns come from the caller */ }
        return new Raw(heads, rows, trunc[0], num);
    }

    private static List<String> sums(List<List<String>> rows, int width, int... sumCols) {
        List<String> t = new ArrayList<>(java.util.Collections.nCopies(width, ""));
        t.set(0, "Total");
        for (int c : sumCols) { BigDecimal s = BigDecimal.ZERO; for (List<String> r : rows) { try { s = s.add(new BigDecimal(r.get(c))); } catch (NumberFormatException e) { /* dash */ } } t.set(c, fmt(s.scale() == 0 ? s : s.setScale(2, RoundingMode.HALF_UP))); }
        return t;
    }

    private String yearName(Long id) { return id == null ? null : jdbc.query("select name from academic_years where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElse(null); }
    private String className(Long id) { return id == null ? null : jdbc.query("select name from school_classes where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElse(null); }
    private String sectionName(Long id) { return id == null ? null : jdbc.query("select name from sections where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElse(null); }
    private String termName(Long id) { return id == null ? null : jdbc.query("select name from terms where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElse(null); }
    private String subjectName(Long id) { return id == null ? null : jdbc.query("select name from subjects where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElse(null); }

    private String subtitle(String yearLabel, RunBody b) {
        List<String> p = new ArrayList<>();
        String y = yearName(b.yearId()); p.add(yearLabel + ": " + (y == null ? "All" : y));
        if (b.termId() != null) p.add("Term: " + termName(b.termId()));
        if (b.classId() != null) p.add("Grade: " + className(b.classId()));
        if (b.sectionId() != null) p.add("Section: " + sectionName(b.sectionId()));
        if (b.subjectId() != null) p.add("Subject: " + subjectName(b.subjectId()));
        return String.join(" · ", p);
    }

    private static Table table(String title, String sub, List<String> heads, Raw r, List<String> total, String note) {
        List<Boolean> num = r.numeric().isEmpty() ? java.util.Collections.nCopies(heads.size(), false) : r.numeric();
        return new Table(title, sub, heads, num, r.rows(), total, r.truncated(), note);
    }

    // ------------------------------------------------------------------ canned reports

    public Table run(RunBody b) {
        if (b == null || b.report() == null) throw ApiException.badRequest("Choose a report.");
        Report rep = REPORTS.stream().filter((r) -> r.code().equals(b.report())).findFirst().orElseThrow(() -> ApiException.badRequest("Unknown report."));
        if (!rep.available()) throw ApiException.badRequest(rep.reason());
        long i = inst();
        List<Object> a = new ArrayList<>(List.of(i));
        String sub;
        switch (rep.code()) {
            case "STU_ENROLL_CLASS" -> {
                StringBuilder w = new StringBuilder(" where s.institution_id = ? and s.active");
                scope(w, a, b, "c.academic_year_id", "c.id", "sec.id");
                Raw r = query("select c.name, coalesce(sec.name, '—'), count(*) from students s join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id" + w
                        + " group by c.id, c.name, c.grade_level, sec.id, sec.name order by coalesce(c.grade_level, -1), c.name, sec.name nulls last", a, MAX_ROWS);
                List<String> h = List.of("Grade", "Section", "Enrolled");
                return table("Student Enrollment", subtitle("Academic Year", b), h, r, sums(r.rows(), 3, 2), "Counts active students who are placed in a grade.");
            }
            case "STU_ENROLL_STATUS" -> {
                StringBuilder w = new StringBuilder(" where s.institution_id = ?");
                scope(w, a, b, "c.academic_year_id", "c.id", "sec.id");
                Raw r = query("select c.name, count(*) filter (where s.active), count(*) filter (where not s.active), count(*) from students s join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id" + w
                        + " group by c.id, c.name, c.grade_level order by coalesce(c.grade_level, -1), c.name", a, MAX_ROWS);
                return table("Active and Inactive Students", subtitle("Academic Year", b), List.of("Grade", "Active", "Inactive", "Total"), r, sums(r.rows(), 4, 1, 2, 3), null);
            }
            case "ADM_STATUS" -> {
                StringBuilder w = new StringBuilder(" where a.institution_id = ?");
                if (b.yearId() != null) { w.append(" and a.academic_year_id = ?"); a.add(b.yearId()); }
                if (b.classId() != null) { w.append(" and a.class_id = ?"); a.add(b.classId()); }
                Raw r = query("select replace(initcap(lower(a.stage)), '_', ' '), count(*) from school_applications a" + w + " group by a.stage order by count(*) desc, a.stage", a, MAX_ROWS);
                return table("Applications by Status", subtitle("Intake", new RunBody(b.report(), b.yearId(), b.classId(), null, null, null)), List.of("Status", "Applications"), r, sums(r.rows(), 2, 1), "Drafts are included here; they are not counted in the conversion report.");
            }
            case "ADM_CONVERSION" -> {
                StringBuilder w = new StringBuilder(" where a.institution_id = ? and a.stage <> 'DRAFT'");
                if (b.yearId() != null) { w.append(" and a.academic_year_id = ?"); a.add(b.yearId()); }
                if (b.classId() != null) { w.append(" and a.class_id = ?"); a.add(b.classId()); }
                Raw r = query("select coalesce(c.name, a.grade, 'Not specified'), count(*), count(*) filter (where exists (select 1 from school_offers o where o.application_id = a.id)), count(*) filter (where a.stage = 'ADMITTED'),"
                        + " round(100.0 * count(*) filter (where a.stage = 'ADMITTED') / nullif(count(*), 0), 1) from school_applications a left join school_classes c on c.id = a.class_id" + w
                        + " group by 1 order by min(coalesce(c.grade_level, 99)), 1", a, MAX_ROWS);
                List<String> t = sums(r.rows(), 5, 1, 2, 3);
                int apps = Integer.parseInt(t.get(1)), adm = Integer.parseInt(t.get(3));
                t.set(4, apps == 0 ? "—" : BigDecimal.valueOf(adm * 100.0 / apps).setScale(1, RoundingMode.HALF_UP).toPlainString());
                return table("Application to Admission Rate", subtitle("Intake", new RunBody(b.report(), b.yearId(), b.classId(), null, null, null)), List.of("Grade", "Applications", "Offers Issued", "Admitted", "Admission Rate (%)"), r, t, "Counts submitted applications (not drafts). Rate = admitted ÷ applications.");
            }
            case "FEE_TERM" -> {
                StringBuilder w = new StringBuilder(" where v.institution_id = ?");
                if (b.yearId() != null) { w.append(" and v.academic_year_id = ?"); a.add(b.yearId()); }
                if (b.termId() != null) { w.append(" and v.term_id = ?"); a.add(b.termId()); }
                String from = " from school_invoice_v v left join terms t on t.id = v.term_id";
                if (b.classId() != null) { from += " join students s on s.id = v.student_id"; w.append(" and s.school_class_id = ?"); a.add(b.classId()); }
                Raw r = query("select coalesce(t.name, 'No term'), sum(v.net), sum(v.paid), sum(v.balance), round(100.0 * sum(v.paid) / nullif(sum(v.net), 0), 1)" + from + w + " group by t.id, t.name order by min(t.start_date) nulls last, 1", a, MAX_ROWS);
                List<String> t = sums(r.rows(), 5, 1, 2, 3);
                BigDecimal net = new BigDecimal(t.get(1)), paid = new BigDecimal(t.get(2));
                t.set(4, net.signum() == 0 ? "—" : paid.multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP).toPlainString());
                return table("Term-wise Fee Collection", subtitle("Academic Year", b), List.of("Term", "Payable (₹)", "Collected (₹)", "Outstanding (₹)", "Collected (%)"), r, t, "Payable is the fee after approved concessions and scholarships. Collected is net of refunds.");
            }
            case "FEE_OUTSTANDING" -> {
                StringBuilder w = new StringBuilder(" where v.institution_id = ? and v.balance > 0");
                if (b.yearId() != null) { w.append(" and v.academic_year_id = ?"); a.add(b.yearId()); }
                if (b.termId() != null) { w.append(" and v.term_id = ?"); a.add(b.termId()); }
                if (b.classId() != null) { w.append(" and c.id = ?"); a.add(b.classId()); }
                Raw r = query("select c.name, count(distinct v.student_id), count(*), sum(v.balance) from school_invoice_v v join students s on s.id = v.student_id join school_classes c on c.id = s.school_class_id" + w + " group by c.id, c.name, c.grade_level order by coalesce(c.grade_level, -1), c.name", a, MAX_ROWS);
                List<String> t = sums(r.rows(), 4, 2, 3);
                t.set(1, String.valueOf(jdbc.queryForObject("select count(distinct v.student_id) from school_invoice_v v join students s on s.id = v.student_id join school_classes c on c.id = s.school_class_id" + w, Long.class, a.toArray())));
                return table("Pending Fees by Grade", subtitle("Academic Year", b), List.of("Grade", "Students with dues", "Open invoices", "Outstanding (₹)"), r, t, "Grade is the student's current grade.");
            }
            case "ACA_GRADE" -> {
                StringBuilder w = new StringBuilder(" where sub.institution_id = ? and sub.marks is not null");
                scope(w, a, b, "c.academic_year_id", "c.id", "sec.id");
                if (b.subjectId() != null) { w.append(" and asg.subject_id = ?"); a.add(b.subjectId()); }
                Raw r = query("select c.name, sec.name, count(distinct asg.id), count(*), round(avg(sub.marks * 100.0 / asg.max_marks), 1) from school_assignment_submissions sub join school_assignments asg on asg.id = sub.assignment_id join sections sec on sec.id = asg.section_id join school_classes c on c.id = sec.school_class_id" + w
                        + " group by c.id, c.name, c.grade_level, sec.id, sec.name order by coalesce(c.grade_level, -1), c.name, sec.name", a, MAX_ROWS);
                return table("Grade-wise Assignment Results", subtitle("Academic Year", b), List.of("Grade", "Section", "Assignments", "Marked Submissions", "Average (%)"), r, sums(r.rows(), 5, 2, 3), "Based on marks entered against assignments. Exam results aren't available because Examinations isn't built yet.");
            }
            case "ACA_SUBJECT" -> {
                StringBuilder w = new StringBuilder(" where sub.institution_id = ? and sub.marks is not null");
                scope(w, a, b, "c.academic_year_id", "c.id", "sec.id");
                if (b.subjectId() != null) { w.append(" and asg.subject_id = ?"); a.add(b.subjectId()); }
                Raw r = query("select su.name, count(distinct asg.id), count(*), round(avg(sub.marks * 100.0 / asg.max_marks), 1), round(max(sub.marks * 100.0 / asg.max_marks), 1), round(min(sub.marks * 100.0 / asg.max_marks), 1) from school_assignment_submissions sub join school_assignments asg on asg.id = sub.assignment_id join subjects su on su.id = asg.subject_id join sections sec on sec.id = asg.section_id join school_classes c on c.id = sec.school_class_id" + w
                        + " group by su.id, su.name order by su.name", a, MAX_ROWS);
                return table("Subject-wise Analysis", subtitle("Academic Year", b), List.of("Subject", "Assignments", "Marked Submissions", "Average (%)", "Highest (%)", "Lowest (%)"), r, sums(r.rows(), 6, 1, 2), "Based on marks entered against assignments. Exam results aren't available because Examinations isn't built yet.");
            }
            default -> throw ApiException.badRequest("Unknown report.");
        }
    }

    private static void scope(StringBuilder w, List<Object> a, RunBody b, String yearCol, String classCol, String sectionCol) {
        if (b.yearId() != null) { w.append(" and ").append(yearCol).append(" = ?"); a.add(b.yearId()); }
        if (b.classId() != null) { w.append(" and ").append(classCol).append(" = ?"); a.add(b.classId()); }
        if (b.sectionId() != null) { w.append(" and ").append(sectionCol).append(" = ?"); a.add(b.sectionId()); }
    }

    // ------------------------------------------------------------------ custom reports

    private Src source(String code) { return SOURCES.stream().filter((s) -> s.code().equals(code)).findFirst().orElse(null); }

    public Table runCustom(CustomBody b) {
        Map<String, String> e = new LinkedHashMap<>();
        Src s = b.source() == null ? null : source(b.source().trim().toUpperCase());
        if (s == null) e.put("source", "Choose a data source.");
        List<Col> cols = new ArrayList<>();
        if (s != null) {
            for (String k : b.columns() == null ? List.<String>of() : b.columns()) {
                Col c = s.cols().stream().filter((x) -> x.key().equals(k)).findFirst().orElse(null);
                if (c == null) { e.put("columns", "Unknown column: " + k); break; }
                if (cols.contains(c)) continue;
                cols.add(c);
            }
            if (cols.isEmpty() && !e.containsKey("columns")) e.put("columns", "Choose at least one column.");
            if (cols.size() > 12) e.put("columns", "Choose 12 columns or fewer.");
        }
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        boolean grouped = cols.stream().anyMatch(Col::agg);
        if (grouped && cols.stream().anyMatch((c) -> !c.agg() && c.numeric())) throw new FieldErrorsException(Map.of("columns", "Per-row amounts can't be mixed with totals. Pick either the per-row columns or the total columns."));
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where " + s.instCol() + " = ?");
        if (b.yearId() != null) { w.append(" and ").append(s.yearCol()).append(" = ?"); a.add(b.yearId()); }
        if (b.classId() != null && s.classCol() != null) { w.append(" and ").append(s.classCol()).append(" = ?"); a.add(b.classId()); }
        if (b.sectionId() != null && s.sectionCol() != null) { w.append(" and ").append(s.sectionCol()).append(" = ?"); a.add(b.sectionId()); }
        String select = cols.stream().map(Col::expr).collect(Collectors.joining(", "));
        String group = grouped ? " group by " + cols.stream().filter((c) -> !c.agg()).map(Col::expr).collect(Collectors.joining(", ")) : "";
        if (grouped && group.equals(" group by ")) group = "";
        String order = " order by " + (grouped ? cols.stream().filter((c) -> !c.agg()).map(Col::expr).collect(Collectors.joining(", ")) : cols.get(0).expr());
        if (order.equals(" order by ")) order = "";
        Raw r = query("select " + select + " from " + s.from() + w + group + order, a, MAX_ROWS);
        List<String> heads = cols.stream().map(Col::label).toList();
        List<Boolean> num = cols.stream().map(Col::numeric).toList();
        List<String> total = null;
        int[] aggIdx = java.util.stream.IntStream.range(0, cols.size()).filter((k) -> cols.get(k).agg()).toArray();
        if (grouped && !r.rows().isEmpty()) { total = sums(r.rows(), cols.size(), aggIdx); if (aggIdx[0] == 0) total.set(0, "Total"); }
        List<String> sub = new ArrayList<>();
        String y = yearName(b.yearId()); if (b.yearId() != null && y != null) sub.add("Academic Year: " + y);
        if (b.classId() != null && s.classCol() != null) sub.add("Grade: " + className(b.classId()));
        if (b.sectionId() != null && s.sectionCol() != null) sub.add("Section: " + sectionName(b.sectionId()));
        String title = b.name() == null || b.name().isBlank() ? s.label() + " report" : b.name().trim();
        return new Table(title, sub.isEmpty() ? "All records · " + s.label() : String.join(" · ", sub), heads, num, r.rows(), total, r.truncated(), r.truncated() ? "Showing the first " + MAX_ROWS + " rows. Add filters to narrow it." : null);
    }

    @Transactional
    public Map<String, Object> save(CustomBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim();
        if (name.isEmpty()) e.put("name", "Required.");
        else if (name.length() > 120) e.put("name", "Keep the name under 120 characters.");
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        runCustom(b); // validates source and columns
        String cols = String.join(",", b.columns());
        Long id = jdbc.queryForObject("insert into school_saved_reports (institution_id, name, source, columns, year_id, class_id, section_id, created_by) values (?,?,?,?,?,?,?,?) on conflict (institution_id, lower(name)) do update set source = excluded.source, columns = excluded.columns, year_id = excluded.year_id, class_id = excluded.class_id, section_id = excluded.section_id, updated_at = now() returning id",
                Long.class, i, name, b.source().trim().toUpperCase(), cols, b.yearId(), b.classId(), b.sectionId(), current.principal().email());
        audit.record("REPORT_SAVED", "REPORT", id, i, "Custom report \"" + name + "\" saved");
        return Map.of("id", id);
    }

    public List<Saved> saved() {
        return jdbc.query("select id, name, source, columns, year_id, class_id, section_id, created_by from school_saved_reports where institution_id = ? order by lower(name)",
                (rs, n) -> new Saved(rs.getLong(1), rs.getString(2), rs.getString(3), Arrays.asList(rs.getString(4).split(",")), rs.getObject(5) == null ? null : rs.getLong(5), rs.getObject(6) == null ? null : rs.getLong(6), rs.getObject(7) == null ? null : rs.getLong(7), rs.getString(8)), inst());
    }

    @Transactional
    public void delete(Long id) {
        String name = jdbc.query("select name from school_saved_reports where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Report"));
        jdbc.update("delete from school_saved_reports where id = ?", id);
        audit.record("REPORT_DELETED", "REPORT", id, inst(), "Custom report \"" + name + "\" deleted");
    }
}
