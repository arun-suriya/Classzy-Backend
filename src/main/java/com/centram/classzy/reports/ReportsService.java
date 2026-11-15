package com.centram.classzy.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.reports.ReportsDto.Content;
import com.centram.classzy.reports.ReportsDto.GenerateBody;
import com.centram.classzy.reports.ReportsDto.Generated;
import com.centram.classzy.reports.ReportsDto.GeneratedRow;
import com.centram.classzy.reports.ReportsDto.Metric;
import com.centram.classzy.reports.ReportsDto.Overview;
import com.centram.classzy.reports.ReportsDto.ScheduleBody;
import com.centram.classzy.reports.ReportsDto.ScheduleRow;
import com.centram.classzy.reports.ReportsDto.Slice;
import com.centram.classzy.reports.ReportsDto.Stats;
import com.centram.classzy.reports.ReportsDto.Template;
import com.centram.classzy.reports.ReportsDto.TopInstitution;
import com.centram.classzy.reports.ReportsDto.TrendPoint;
import com.centram.classzy.security.AppPrincipal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform analytics and report generation. Reports are built from live data, stored as CSV and turned into PDF/Excel/CSV
 * downloads by the browser. Scheduled reports are produced the next time the lists are read after they fall due, and are
 * not emailed (no mail provider is connected).
 */
@Service
@Transactional
public class ReportsService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+$");
    static final Set<String> FORMATS = Set.of("PDF", "EXCEL", "CSV");
    static final Set<String> FREQUENCIES = Set.of("DAILY", "WEEKLY", "MONTHLY");

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();
    private static void t(String code, String name, String cat, String desc, String icon, boolean ok, String note) { TEMPLATES.put(code, new Template(code, name, cat, desc, icon, ok, note)); }
    static {
        t("ENROLLMENT", "Student Enrollment Report", "STUDENT", "Enrollment trends and demographics", "building", true, null);
        t("ATTENDANCE", "Attendance Summary", "ACADEMIC", "Daily/weekly attendance reports", "list", false, "Attendance isn't recorded in the platform yet.");
        t("FEE_COLLECTION", "Fee Collection Summary", "FINANCE", "Collection and outstanding fees report", "receipt", true, null);
        t("USER_ACTIVITY", "User Activity Report", "USAGE", "Platform usage and login activity", "user", true, null);
        t("MODULE_USAGE", "Module Usage Report", "USAGE", "Which institutions use which modules", "grid", true, null);
        t("TEACHER_ROSTER", "Teacher Roster", "TEACHER", "Teachers by institution", "graduation", true, null);
        t("INSTITUTION_SUMMARY", "Institution Summary", "OPERATIONAL", "Plans, status, users and students per institution", "building", true, null);
        t("CLASS_STRENGTH", "Class Strength Report", "ACADEMIC", "Students per class and section", "list", true, null);
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ReportsService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ overview

    @Transactional(readOnly = true)
    public Overview overview(int months, Long institutionId, String module, int topDays) {
        int mo = months == 3 || months == 6 || months == 12 ? months : 6;
        int days = topDays == 7 || topDays == 30 || topDays == 90 ? topDays : 30;
        List<Long> scope = scope(institutionId, module);
        String u = cond("u.institution_id", scope), s = cond("s.institution_id", scope), p = cond("p.institution_id", scope), i = cond("i.id", scope);

        long instNow = n("select count(*) from institutions i where i.lifecycle <> 'ARCHIVED'" + i);
        long instPrev = n("select count(*) from institutions i where i.lifecycle <> 'ARCHIVED' and i.created_at < date_trunc('month', now())" + i);
        long usersNow = n("select count(*) from users u where u.active" + u);
        long usersPrev = n("select count(*) from users u where u.active and u.created_at < date_trunc('month', now())" + u);
        long stuNow = n("select count(*) from students s where s.active" + s);
        long stuPrev = n("select count(*) from students s where s.active and s.created_at < date_trunc('month', now())" + s);
        BigDecimal revTotal = jdbc.queryForObject("select coalesce(sum(p.amount), 0) from payments p where 1 = 1" + p, BigDecimal.class);
        BigDecimal revThis = jdbc.queryForObject("select coalesce(sum(p.amount), 0) from payments p where p.paid_on >= date_trunc('month', now())::date" + p, BigDecimal.class);
        BigDecimal revLast = jdbc.queryForObject("select coalesce(sum(p.amount), 0) from payments p where p.paid_on >= (date_trunc('month', now()) - interval '1 month')::date and p.paid_on < date_trunc('month', now())::date" + p, BigDecimal.class);

        Stats stats = new Stats(new Metric(BigDecimal.valueOf(instNow), pct(instNow, instPrev)), new Metric(BigDecimal.valueOf(usersNow), pct(usersNow, usersPrev)),
                new Metric(BigDecimal.valueOf(stuNow), pct(stuNow, stuPrev)), new Metric(null, null),
                new Metric(revTotal, pct(revThis.doubleValue(), revLast.doubleValue())));

        List<TrendPoint> trend = jdbc.query("select to_char(m, 'Mon'), "
                + "(select count(*) from students s where s.active and s.created_at >= m and s.created_at < m + interval '1 month'" + s + "), "
                + "(select count(*) from students s where s.active and s.created_at < m + interval '1 month'" + s + ") "
                + "from generate_series(date_trunc('month', now()) - interval '" + (mo - 1) + " month', date_trunc('month', now()), interval '1 month') m order by m",
                (rs, r) -> new TrendPoint(rs.getString(1), rs.getLong(2), rs.getLong(3)));

        List<Slice> slices = new ArrayList<>();
        List<Object[]> mods = jdbc.query("select m.name, count(i.id) c from platform_modules m join institution_modules im on im.module_code = m.code "
                + "join institutions i on i.id = im.institution_id and i.lifecycle <> 'ARCHIVED'" + cond("i.id", scope) + " group by m.name order by c desc, m.name",
                (rs, r) -> new Object[] { rs.getString(1), rs.getLong(2) });
        long modTotal = mods.stream().mapToLong(o -> (Long) o[1]).sum();
        long others = 0;
        for (int k = 0; k < mods.size(); k++) {
            long v = (Long) mods.get(k)[1];
            if (k < 5) slices.add(new Slice((String) mods.get(k)[0], v, modTotal == 0 ? 0 : round1(100.0 * v / modTotal))); else others += v;
        }
        if (others > 0) slices.add(new Slice("Others", others, round1(100.0 * others / modTotal)));

        List<TopInstitution> top = jdbc.query("select i.id, i.name, count(*) c from users u join institutions i on i.id = u.institution_id where u.active and u.last_login_at >= now() - interval '" + days + " day'"
                + cond("u.institution_id", scope) + " group by i.id, i.name order by c desc, i.name limit 5", (rs, r) -> new TopInstitution(rs.getLong(1), rs.getString(2), rs.getLong(3)));
        return new Overview(stats, trend, slices, modTotal, top);
    }

    private List<Long> scope(Long institutionId, String module) {
        List<Long> ids = null;
        if (institutionId != null) ids = List.of(institutionId);
        if (module != null && !module.isBlank()) {
            List<Long> m = jdbc.queryForList("select institution_id from institution_modules where module_code = ?", Long.class, module.trim().toUpperCase());
            ids = ids == null ? m : ids.stream().filter(m::contains).toList();
        }
        return ids;
    }

    private static String cond(String col, List<Long> ids) {
        if (ids == null) return "";
        return ids.isEmpty() ? " and 1 = 0" : " and " + col + " in (" + ids.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
    }

    private long n(String sql) { return jdbc.queryForObject(sql, Long.class); }
    private static double round1(double v) { return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue(); }
    private static Double pct(double now, double prev) { return prev <= 0 ? null : round1((now - prev) * 100.0 / prev); }

    // ------------------------------------------------------------------ templates and generation

    public List<Template> templates() { return new ArrayList<>(TEMPLATES.values()); }

    private static Template template(String code) {
        Template t = code == null ? null : TEMPLATES.get(code.trim().toUpperCase());
        if (t == null) throw ApiException.badRequest("Choose a report.");
        return t;
    }

    public Generated generate(GenerateBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        Template t = null;
        try { t = template(b.template()); } catch (ApiException e) { err.put("template", "Choose a report."); }
        String format = b.format() == null ? "PDF" : b.format().trim().toUpperCase();
        if (!FORMATS.contains(format)) err.put("format", "Choose PDF, Excel or CSV.");
        int days = b.days() == null ? 30 : b.days();
        if (days < 1 || days > 730) err.put("days", "Choose a date range.");
        if (b.institutionId() != null && n("select count(*) from institutions where id = " + b.institutionId()) == 0) err.put("institutionId", "Institution not found.");
        if (t != null && !t.available()) err.put("template", t.note());
        ScheduleBody sb = b.schedule();
        if (sb != null) err.putAll(scheduleErrors(sb, true));
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        GeneratedRow row = build(t, format, days, b.institutionId(), actor(), null);
        ScheduleRow sched = null;
        if (sb != null) sched = createSchedule(new ScheduleBody(t.name(), t.code(), format, sb.frequency(), days, b.institutionId(), sb.recipients()));
        return new Generated(row, content(row.id()).csv(), sched);
    }

    private GeneratedRow build(Template t, String format, int days, Long inst, String by, Long scheduleId) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = data(t.code(), days, inst, headers);
        StringBuilder csv = new StringBuilder();
        csv.append(headers.stream().map(ReportsService::q).collect(Collectors.joining(","))).append('\n');
        for (List<String> r : rows) csv.append(r.stream().map(ReportsService::q).collect(Collectors.joining(","))).append('\n');
        Long id = jdbc.queryForObject("insert into generated_reports (name, template, format, range_days, institution_id, row_count, content, generated_by, schedule_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, t.name(), t.code(), format, days, inst, rows.size(), csv.toString(), by, scheduleId);
        audit.record("REPORT_GENERATED", "REPORT", id, inst, "Generated " + t.name() + " (" + rows.size() + " rows)");
        return generated(id);
    }

    private static String q(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s; // keep spreadsheets from running formulas
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private List<List<String>> data(String code, int days, Long inst, List<String> headers) {
        Timestamp since = Timestamp.from(ZonedDateTime.now(IST).minusDays(days).toInstant());
        java.sql.Date sinceDate = java.sql.Date.valueOf(ZonedDateTime.now(IST).minusDays(days).toLocalDate());
        String ic = inst == null ? "" : " and %s = " + inst;
        switch (code) {
            case "ENROLLMENT":
                headers.addAll(List.of("Admission No", "Student", "Class", "Section", "Institution", "Enrolled On"));
                return fetch("select s.admission_no, s.full_name, coalesce(c.name,''), coalesce(sec.name,''), i.name, s.created_at::date from students s join institutions i on i.id = s.institution_id "
                        + "left join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id where s.active and s.created_at >= ?" + ic.formatted("s.institution_id") + " order by i.name, c.name, s.full_name", since);
            case "FEE_COLLECTION":
                headers.addAll(List.of("Institution", "Paid On", "Amount (INR)", "Method", "Plan", "Reference"));
                return fetch("select i.name, p.paid_on, p.amount, p.method, coalesce(p.plan_type,''), coalesce(p.reference,'') from payments p join institutions i on i.id = p.institution_id where p.paid_on >= ?"
                        + ic.formatted("p.institution_id") + " order by p.paid_on desc, i.name", sinceDate);
            case "USER_ACTIVITY":
                headers.addAll(List.of("User", "Email", "Account Type", "Role", "Institution", "Status", "Last Login"));
                return fetch("select u.full_name, u.email, u.user_type, u.role, coalesce(i.name,'Platform'), u.status, to_char(u.last_login_at at time zone 'Asia/Kolkata', 'DD Mon YYYY HH24:MI') from users u left join institutions i on i.id = u.institution_id "
                        + "where u.last_login_at >= ?" + ic.formatted("u.institution_id") + " order by u.last_login_at desc", since);
            case "MODULE_USAGE":
                headers.addAll(List.of("Module", "Category", "Institutions Using", "Platform Status"));
                return fetch("select m.name, m.category, count(i.id), case when m.enabled then 'Enabled' else 'Disabled' end from platform_modules m left join institution_modules im on im.module_code = m.code"
                        + (inst == null ? "" : " and im.institution_id = " + inst) + " left join institutions i on i.id = im.institution_id and i.lifecycle <> 'ARCHIVED' group by m.code, m.name, m.category, m.enabled, m.position order by m.position");
            case "TEACHER_ROSTER":
                headers.addAll(List.of("Teacher", "Email", "Phone", "Institution"));
                return fetch("select t.full_name, coalesce(t.email,''), coalesce(t.phone,''), i.name from teachers t join institutions i on i.id = t.institution_id where t.active" + ic.formatted("t.institution_id") + " order by i.name, t.full_name");
            case "INSTITUTION_SUMMARY":
                headers.addAll(List.of("Institution", "Type", "Plan", "Status", "Users", "Students", "Teachers", "Subscription Ends"));
                return fetch("select i.name, i.institution_type, coalesce(i.plan_type,''), i.lifecycle, (select count(*) from users u where u.institution_id = i.id and u.active), (select count(*) from students s where s.institution_id = i.id and s.active), "
                        + "(select count(*) from teachers t where t.institution_id = i.id and t.active), coalesce(i.subscription_end::text,'') from institutions i where i.lifecycle <> 'ARCHIVED'" + ic.formatted("i.id") + " order by lower(i.name)");
            case "CLASS_STRENGTH":
                headers.addAll(List.of("Institution", "Class", "Section", "Students", "Capacity"));
                return fetch("select i.name, c.name, sec.name, (select count(*) from students s where s.section_id = sec.id and s.active), coalesce(sec.max_students::text,'') from sections sec "
                        + "join school_classes c on c.id = sec.school_class_id join institutions i on i.id = sec.institution_id where sec.active" + ic.formatted("sec.institution_id") + " order by i.name, c.grade_level nulls first, c.name, sec.name");
            default:
                throw ApiException.badRequest("This report can't be generated.");
        }
    }

    private List<List<String>> fetch(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            int cols = rs.getMetaData().getColumnCount();
            List<String> r = new ArrayList<>(cols);
            for (int c = 1; c <= cols; c++) { Object o = rs.getObject(c); r.add(o == null ? "" : o.toString()); }
            return r;
        }, args);
    }

    // ------------------------------------------------------------------ generated reports

    private static final String GEN = "select g.id, g.name, g.template, g.format, g.status, g.row_count, i.name, g.generated_by, g.generated_at from generated_reports g left join institutions i on i.id = g.institution_id";

    private GeneratedRow gen(ResultSet rs) throws SQLException {
        Template t = TEMPLATES.get(rs.getString(3));
        return new GeneratedRow(rs.getLong(1), rs.getString(2), rs.getString(3), t == null ? "CUSTOM" : t.category(), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9).toInstant().toString());
    }

    private GeneratedRow generated(Long id) {
        List<GeneratedRow> r = jdbc.query(GEN + " where g.id = ?", (rs, i) -> gen(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Report");
        return r.get(0);
    }

    @Transactional
    public List<GeneratedRow> recent(String category, int limit) {
        runDueSchedules();
        String codes = codesFor(category);
        return jdbc.query(GEN + (codes == null ? "" : " where g.template in (" + codes + ")") + " order by g.generated_at desc, g.id desc limit ?", (rs, i) -> gen(rs), Math.min(Math.max(limit, 1), 50));
    }

    @Transactional(readOnly = true)
    public Content content(Long id) {
        List<Content> r = jdbc.query("select name, format, content from generated_reports where id = ?", (rs, i) -> new Content(rs.getString(1), rs.getString(2), rs.getString(3)), id);
        if (r.isEmpty()) throw ApiException.notFound("Report");
        return r.get(0);
    }

    public void deleteGenerated(Long id) {
        GeneratedRow g = generated(id);
        jdbc.update("delete from generated_reports where id = ?", id);
        audit.record("REPORT_DELETED", "REPORT", id, null, "Deleted " + g.name());
    }

    private static String codesFor(String category) {
        if (category == null || category.isBlank() || category.equals("ALL")) return null;
        String c = category.trim().toUpperCase();
        String codes = TEMPLATES.values().stream().filter(t -> t.category().equals(c)).map(t -> "'" + t.code() + "'").collect(Collectors.joining(","));
        return codes.isEmpty() ? "''" : codes;
    }

    // ------------------------------------------------------------------ schedules

    private static final String SCH = "select s.id, s.name, s.template, s.format, s.frequency, s.range_days, s.institution_id, i.name, s.recipients, s.status, s.next_run, s.last_run from report_schedules s left join institutions i on i.id = s.institution_id";

    private ScheduleRow sch(ResultSet rs) throws SQLException {
        Template t = TEMPLATES.get(rs.getString(3));
        Timestamp last = rs.getTimestamp(12);
        return new ScheduleRow(rs.getLong(1), rs.getString(2), rs.getString(3), t == null ? "CUSTOM" : t.category(), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getObject(7) == null ? null : rs.getLong(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getTimestamp(11).toInstant().toString(), last == null ? null : last.toInstant().toString());
    }

    private ScheduleRow schedule(Long id) {
        List<ScheduleRow> r = jdbc.query(SCH + " where s.id = ?", (rs, i) -> sch(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Schedule");
        return r.get(0);
    }

    @Transactional
    public List<ScheduleRow> schedules(String category) {
        runDueSchedules();
        String codes = codesFor(category);
        return jdbc.query(SCH + (codes == null ? "" : " where s.template in (" + codes + ")") + " order by s.status, s.next_run, s.id", (rs, i) -> sch(rs));
    }

    private Map<String, String> scheduleErrors(ScheduleBody b, boolean nested) {
        Map<String, String> err = new LinkedHashMap<>();
        if (!nested) {
            if (b.name() == null || b.name().isBlank()) err.put("name", "Required."); else if (b.name().trim().length() > 150) err.put("name", "Use 150 characters or fewer.");
            try { Template t = template(b.template()); if (!t.available()) err.put("template", t.note()); } catch (ApiException e) { err.put("template", "Choose a report."); }
            if (b.format() == null || !FORMATS.contains(b.format().trim().toUpperCase())) err.put("format", "Choose PDF, Excel or CSV.");
            if (b.days() != null && (b.days() < 1 || b.days() > 730)) err.put("days", "Choose a date range.");
        }
        if (b.frequency() == null || !FREQUENCIES.contains(b.frequency().trim().toUpperCase())) err.put("frequency", "Choose how often.");
        List<String> mails = emails(b.recipients());
        if (mails.size() > 10) err.put("recipients", "Add 10 recipients or fewer.");
        else if (mails.stream().anyMatch(m -> !EMAIL.matcher(m).matches())) err.put("recipients", "Enter valid email addresses separated by commas.");
        return err;
    }

    private static List<String> emails(String s) {
        return s == null || s.isBlank() ? List.of() : java.util.Arrays.stream(s.split("[,;\\s]+")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static Timestamp firstRun(String freq) {
        ZonedDateTime n = ZonedDateTime.now(IST);
        ZonedDateTime next = switch (freq) {
            case "DAILY" -> n.plusDays(1);
            case "WEEKLY" -> n.plusWeeks(1);
            default -> n.plusMonths(1).withDayOfMonth(1);
        };
        return Timestamp.from(next.withHour(6).withMinute(0).withSecond(0).withNano(0).toInstant());
    }

    private static Timestamp following(Timestamp from, String freq) {
        ZonedDateTime z = from.toInstant().atZone(IST);
        ZonedDateTime next = freq.equals("DAILY") ? z.plusDays(1) : freq.equals("WEEKLY") ? z.plusWeeks(1) : z.plusMonths(1);
        return Timestamp.from(next.toInstant());
    }

    public ScheduleRow createSchedule(ScheduleBody b) {
        Map<String, String> err = scheduleErrors(b, false);
        if (b.institutionId() != null && n("select count(*) from institutions where id = " + b.institutionId()) == 0) err.put("institutionId", "Institution not found.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        String freq = b.frequency().trim().toUpperCase();
        Long id = jdbc.queryForObject("insert into report_schedules (name, template, format, frequency, range_days, institution_id, recipients, next_run, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                b.name().trim(), b.template().trim().toUpperCase(), b.format().trim().toUpperCase(), freq, b.days() == null ? 30 : b.days(), b.institutionId(), String.join(", ", emails(b.recipients())), firstRun(freq), actor());
        audit.record("REPORT_SCHEDULED", "REPORT_SCHEDULE", id, b.institutionId(), "Scheduled " + b.name().trim() + " (" + freq.toLowerCase() + ")");
        return schedule(id);
    }

    public ScheduleRow updateSchedule(Long id, ScheduleBody b) {
        schedule(id);
        Map<String, String> err = scheduleErrors(b, false);
        if (b.institutionId() != null && n("select count(*) from institutions where id = " + b.institutionId()) == 0) err.put("institutionId", "Institution not found.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        String freq = b.frequency().trim().toUpperCase();
        jdbc.update("update report_schedules set name = ?, template = ?, format = ?, frequency = ?, range_days = ?, institution_id = ?, recipients = ?, next_run = ? where id = ?",
                b.name().trim(), b.template().trim().toUpperCase(), b.format().trim().toUpperCase(), freq, b.days() == null ? 30 : b.days(), b.institutionId(), String.join(", ", emails(b.recipients())), firstRun(freq), id);
        audit.record("REPORT_SCHEDULE_UPDATED", "REPORT_SCHEDULE", id, null, "Updated schedule " + b.name().trim());
        return schedule(id);
    }

    public ScheduleRow toggle(Long id) {
        ScheduleRow s = schedule(id);
        boolean pause = s.status().equals("ACTIVE");
        jdbc.update("update report_schedules set status = ?, next_run = ? where id = ?", pause ? "PAUSED" : "ACTIVE", pause ? Timestamp.from(java.time.Instant.parse(s.nextRun())) : firstRun(s.frequency()), id);
        audit.record("REPORT_SCHEDULE_UPDATED", "REPORT_SCHEDULE", id, null, (pause ? "Paused " : "Resumed ") + s.name());
        return schedule(id);
    }

    public Generated runNow(Long id) {
        ScheduleRow s = schedule(id);
        GeneratedRow row = build(template(s.template()), s.format(), s.rangeDays(), s.institutionId(), actor(), id);
        jdbc.update("update report_schedules set last_run = now() where id = ?", id);
        return new Generated(row, content(row.id()).csv(), schedule(id));
    }

    public void deleteSchedule(Long id) {
        ScheduleRow s = schedule(id);
        jdbc.update("delete from report_schedules where id = ?", id);
        audit.record("REPORT_SCHEDULE_DELETED", "REPORT_SCHEDULE", id, null, "Deleted schedule " + s.name());
    }

    private void runDueSchedules() {
        List<Object[]> due = jdbc.query("select id, template, format, range_days, institution_id, frequency, next_run from report_schedules where status = 'ACTIVE' and next_run <= now()",
                (rs, i) -> new Object[] { rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getString(6), rs.getTimestamp(7) });
        for (Object[] d : due) {
            Timestamp next = (Timestamp) d[6];
            String freq = (String) d[5];
            do { next = following(next, freq); } while (!next.toInstant().isAfter(java.time.Instant.now()));
            try { build(template((String) d[1]), (String) d[2], (Integer) d[3], (Long) d[4], "Scheduler", (Long) d[0]); }
            catch (RuntimeException ignored) { /* keep the schedule moving even if one run fails */ }
            jdbc.update("update report_schedules set last_run = now(), next_run = ? where id = ?", next, d[0]);
        }
    }

    private static String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppPrincipal p ? p.email() : null;
    }
}
