package com.centram.classzy.school.attend;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.attend.AttnDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Student attendance desk: daily marking, leave, reports, trends and the per-student calendar. */
@Service
@Transactional
public class AttnService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final List<Code> STATUSES = List.of(new Code("PRESENT", "Present"), new Code("ABSENT", "Absent"), new Code("LATE", "Late"), new Code("ON_LEAVE", "On Leave"));
    static final List<Code> LEAVE_TYPES = List.of(new Code("MEDICAL", "Medical Leave"), new Code("FAMILY", "Family Leave"), new Code("FUNCTION", "Function / Event"), new Code("TRAVEL", "Travel"), new Code("OTHER", "Other"));
    static final int MAX_LEAVE_DAYS = 120;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public AttnService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) { this.jdbc = jdbc; this.current = current; this.audit = audit; }

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static LocalDate today() { return LocalDate.now(IST); }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }
    private static String clip(String v, int max) { return v == null || v.trim().isEmpty() ? null : (v.trim().length() > max ? v.trim().substring(0, max) : v.trim()); }
    private static double pct(double a, double b) { return b <= 0 ? 0 : Math.round(a * 1000.0 / b) / 10.0; }
    private static boolean has(List<Code> l, String c) { return c != null && l.stream().anyMatch((x) -> x.code().equals(c)); }
    private static String label(List<Code> l, String c) { return l.stream().filter((x) -> x.code().equals(c)).map(Code::label).findFirst().orElse(c); }

    private static LocalDate parse(String v, String key, Map<String, String> err) {
        if (v == null || v.isBlank()) { err.put(key, "Choose a date."); return null; }
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { err.put(key, "That isn't a valid date."); return null; }
    }
    private static LocalDate need(String v, String what) {
        Map<String, String> e = new LinkedHashMap<>(); LocalDate d = parse(v, what, e);
        if (d == null) throw ApiException.badRequest(e.get(what));
        return d;
    }

    // ------------------------------------------------------------------ settings, calendar helpers

    private record Policy(int min, boolean late, boolean leave, boolean adjust) {}
    private Policy policy() {
        List<Policy> p = jdbc.query("select min_attendance_pct, count_late, count_on_leave, adjustment_enabled from attendance_settings where institution_id = ?",
                (rs, i) -> new Policy(rs.getInt(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)), inst());
        return p.isEmpty() ? new Policy(75, true, false, true) : p.get(0);
    }

    private Set<DayOfWeek> workingDays() {
        Set<DayOfWeek> s = new HashSet<>();
        for (String d : jdbc.queryForList("select day_of_week from institution_working_days where institution_id = ?", String.class, inst())) {
            try { s.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) { /* unknown name */ }
        }
        if (s.isEmpty()) s.addAll(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        return s;
    }

    /** Holiday events by date → title, for a date range. */
    private Map<LocalDate, String> holidays(LocalDate from, LocalDate to) {
        Map<LocalDate, String> out = new HashMap<>();
        jdbc.query("select title, event_date, coalesce(end_date, event_date) from school_events where institution_id = ? and category = 'HOLIDAY' and event_date <= ? and coalesce(end_date, event_date) >= ?", (rs) -> {
            LocalDate a = rs.getDate(2).toLocalDate(), b = rs.getDate(3).toLocalDate();
            for (LocalDate d = a.isBefore(from) ? from : a; !d.isAfter(b) && !d.isAfter(to); d = d.plusDays(1)) out.putIfAbsent(d, rs.getString(1));
        }, inst(), Date.valueOf(to), Date.valueOf(from));
        return out;
    }

    private String offReason(LocalDate d, Set<DayOfWeek> work, Map<LocalDate, String> hol) {
        if (hol.containsKey(d)) return "Holiday – " + hol.get(d);
        if (!work.contains(d.getDayOfWeek())) return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " is not a school day";
        return null;
    }
    private DayInfo dayInfo(LocalDate d) { String r = offReason(d, workingDays(), holidays(d, d)); return new DayInfo(d.toString(), r == null, r); }

    // ------------------------------------------------------------------ options

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        List<Long> yr = jdbc.queryForList("select id from academic_years where institution_id = ? and active order by is_current desc, start_date desc limit 1", Long.class, i);
        Long year = yr.isEmpty() ? null : yr.get(0);
        String yearName = year == null ? null : jdbc.queryForObject("select name from academic_years where id = ?", String.class, year);
        Policy p = policy();
        List<ClassOpt> classes = year == null ? List.of() : jdbc.query("select id, name from school_classes where institution_id = ? and academic_year_id = ? and active order by coalesce(grade_level, -1), name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getString(2)), i, year);
        List<SectionOpt> sections = year == null ? List.of() : jdbc.query("select s.id, c.id, c.name, s.name from sections s join school_classes c on c.id = s.school_class_id where s.institution_id = ? and c.academic_year_id = ? and s.active and c.active order by coalesce(c.grade_level, -1), c.name, s.name",
                (rs, n) -> new SectionOpt(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)), i, year);
        List<TermOpt> terms = year == null ? List.of() : jdbc.query("select id, name, start_date, end_date from terms where institution_id = ? and academic_year_id = ? and active order by start_date", (rs, n) -> new TermOpt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), i, year);
        return new Options(today().toString(), yearName, classes, sections, terms, p.min(), p.late(), p.leave(), p.adjust(), STATUSES, LEAVE_TYPES);
    }

    @Transactional(readOnly = true)
    public List<StudentOpt> students(Long sectionId) {
        if (sectionId == null) throw ApiException.badRequest("Choose a section.");
        return jdbc.query("select s.id, s.admission_no, s.full_name, c.name || ' – ' || sc.name from students s join sections sc on sc.id = s.section_id join school_classes c on c.id = sc.school_class_id where s.institution_id = ? and s.section_id = ? and s.active order by s.full_name",
                (rs, n) -> new StudentOpt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), inst(), sectionId);
    }

    // ------------------------------------------------------------------ overview

    @Transactional(readOnly = true)
    public Overview overview(String dateS, Long classId) {
        LocalDate d = dateS == null || dateS.isBlank() ? today() : need(dateS, "date");
        long i = inst();
        String cls = classId == null ? "" : " and s.school_class_id = ?";
        Object[] base = classId == null ? new Object[] { i } : new Object[] { i, classId };
        long total = count("select count(*) from students s where s.institution_id = ? and s.active" + cls, base);
        Map<String, Long> by = new HashMap<>();
        Object[] a = classId == null ? new Object[] { i, Date.valueOf(d) } : new Object[] { i, Date.valueOf(d), classId };
        jdbc.query("select m.status, count(*) from school_att_marks m join students s on s.id = m.student_id where s.institution_id = ? and s.active and m.att_date = ?" + cls + " group by m.status", (rs) -> { by.put(rs.getString(1), rs.getLong(2)); }, a);
        long late = by.getOrDefault("LATE", 0L), present = by.getOrDefault("PRESENT", 0L) + late, absent = by.getOrDefault("ABSENT", 0L), leave = by.getOrDefault("ON_LEAVE", 0L);
        long unmarked = Math.max(0, total - present - absent - leave);
        List<Recent> recent = jdbc.query("select s.full_name, c.name, m.status from school_att_marks m join students s on s.id = m.student_id left join school_classes c on c.id = s.school_class_id where s.institution_id = ? and m.att_date = ?" + cls + " order by m.marked_at desc, s.full_name limit 8",
                (rs, n) -> new Recent(rs.getString(1), rs.getString(2), rs.getString(3)), a);
        return new Overview(dayInfo(d), total, present, absent, leave, late, unmarked, pct(present, total), pct(absent, total), pct(leave, total), recent);
    }

    // ------------------------------------------------------------------ daily marking

    private record Sec(long id, String name, long classId) {}
    private Sec section(Long id) {
        if (id == null) throw ApiException.badRequest("Choose a grade and section.");
        List<Sec> l = jdbc.query("select s.id, c.name || ' – ' || s.name, c.id from sections s join school_classes c on c.id = s.school_class_id where s.id = ? and s.institution_id = ?", (rs, n) -> new Sec(rs.getLong(1), rs.getString(2), rs.getLong(3)), id, inst());
        if (l.isEmpty()) throw ApiException.notFound("That section wasn't found.");
        return l.get(0);
    }

    private String lock(LocalDate d, DayInfo info, Policy p) {
        if (d.isAfter(today())) return "Attendance can't be taken for a future date.";
        if (!info.schoolDay()) return info.reason() + ", so attendance isn't taken.";
        if (!p.adjust() && d.isBefore(today())) return "Past attendance is locked (adjustments are turned off in Settings).";
        return null;
    }

    @Transactional(readOnly = true)
    public Daily daily(String dateS, Long sectionId) {
        LocalDate d = need(dateS, "date");
        Sec sec = section(sectionId);
        DayInfo info = dayInfo(d);
        String lock = lock(d, info, policy());
        List<DailyRow> rows = jdbc.query("select s.id, s.admission_no, s.full_name, m.status, m.remarks, m.leave_id is not null from students s left join school_att_marks m on m.student_id = s.id and m.att_date = ? where s.institution_id = ? and s.section_id = ? and s.active order by s.full_name",
                (rs, n) -> new DailyRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6)), Date.valueOf(d), inst(), sec.id());
        return new Daily(info, lock == null, lock, sec.name(), rows);
    }

    public Daily saveDaily(DailyBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        LocalDate d = parse(b.date(), "date", err);
        fail(err);
        Sec sec = section(b.sectionId());
        String lock = lock(d, dayInfo(d), policy());
        if (lock != null) throw ApiException.badRequest(lock);
        if (b.rows() == null) throw ApiException.badRequest("There is nothing to save.");
        Set<Long> ids = new HashSet<>(jdbc.queryForList("select id from students where institution_id = ? and section_id = ? and active", Long.class, inst(), sec.id()));
        int saved = 0, cleared = 0;
        for (MarkBody r : b.rows()) {
            if (r.studentId() == null || !ids.contains(r.studentId())) { err.put("row" + r.studentId(), "This student isn't in the section."); continue; }
            String st = r.status() == null ? "" : r.status().trim();
            if (st.isEmpty()) {
                cleared += jdbc.update("delete from school_att_marks where student_id = ? and att_date = ?", r.studentId(), Date.valueOf(d));
                continue;
            }
            if (!has(STATUSES, st)) { err.put("row" + r.studentId(), "Choose Present, Absent, Late or On Leave."); continue; }
            String rem = clip(r.remarks(), 200);
            if (r.remarks() != null && r.remarks().trim().length() > 200) { err.put("row" + r.studentId(), "Remarks can be at most 200 characters."); continue; }
            saved += jdbc.update("insert into school_att_marks (institution_id, student_id, att_date, status, remarks, marked_by, marked_at) values (?, ?, ?, ?, ?, ?, now()) "
                    + "on conflict (student_id, att_date) do update set status = excluded.status, remarks = excluded.remarks, marked_by = excluded.marked_by, marked_at = now(), "
                    + "leave_id = case when school_att_marks.status = excluded.status then school_att_marks.leave_id else null end",
                    inst(), r.studentId(), Date.valueOf(d), st, rem, actor());
        }
        fail(err);
        audit.record("ATTENDANCE_SAVED", "ATTENDANCE", sec.id(), inst(), "Attendance " + d + " - " + sec.name() + " (" + saved + " marked)");
        return daily(d.toString(), sec.id());
    }

    // ------------------------------------------------------------------ reports

    private record Cnt(long p, long l, long a, long v) {
        long total() { return p + l + a + v; }
        double rate(Policy pol) {
            double ok = p + (pol.late() ? l : 0) + (pol.leave() ? v : 0);
            double den = total() - (pol.leave() ? 0 : v);
            return pct(ok, den);
        }
    }
    private static Cnt cnt(java.sql.ResultSet rs, int at) throws java.sql.SQLException { return new Cnt(rs.getLong(at), rs.getLong(at + 1), rs.getLong(at + 2), rs.getLong(at + 3)); }
    private static final String SUMS = "coalesce(sum((m.status = 'PRESENT')::int), 0), coalesce(sum((m.status = 'LATE')::int), 0), coalesce(sum((m.status = 'ABSENT')::int), 0), coalesce(sum((m.status = 'ON_LEAVE')::int), 0)";

    @Transactional(readOnly = true)
    public Table report(String type, String fromS, String toS, Long classId, Long sectionId) {
        Map<String, String> err = new LinkedHashMap<>();
        LocalDate from = parse(fromS, "from", err), to = parse(toS, "to", err);
        fail(err);
        if (to.isBefore(from)) throw ApiException.badRequest("The end date can't be before the start date.");
        if (to.isAfter(today())) to = today();
        if (from.plusDays(400).isBefore(to)) throw ApiException.badRequest("Choose a range of at most 13 months.");
        Policy p = policy();
        StringBuilder where = new StringBuilder(" where s.institution_id = ? and m.att_date between ? and ?");
        List<Object> args = new ArrayList<>(List.of(inst(), Date.valueOf(from), Date.valueOf(to)));
        String scope = "All grades";
        if (sectionId != null) { Sec s = section(sectionId); where.append(" and s.section_id = ?"); args.add(s.id()); scope = s.name(); }
        else if (classId != null) { where.append(" and s.school_class_id = ?"); args.add(classId); scope = jdbc.queryForList("select name from school_classes where id = ? and institution_id = ?", String.class, classId, inst()).stream().findFirst().orElse("Grade"); }
        String sub = scope + " · " + from + " to " + to;
        String note = "Counts recorded attendance only. " + (p.late() ? "Late counts as present. " : "Late does not count as present. ") + (p.leave() ? "On-leave days count as present." : "On-leave days are left out of the percentage.");
        List<Boolean> num;
        List<List<String>> rows = new ArrayList<>();
        switch (type == null ? "" : type) {
            case "ATTENDANCE_SUMMARY" -> {
                jdbc.query("select coalesce(c.name, 'No grade'), " + SUMS + " from school_att_marks m join students s on s.id = m.student_id left join school_classes c on c.id = s.school_class_id" + where + " group by c.name, c.grade_level order by coalesce(c.grade_level, -1), c.name", (rs) -> {
                    Cnt c = cnt(rs, 2);
                    rows.add(List.of(rs.getString(1), c.rate(p) + "%", String.valueOf(c.a()), String.valueOf(c.v()), String.valueOf(c.l())));
                }, args.toArray());
                num = List.of(false, true, true, true, true);
                return new Table("Attendance Summary", sub, List.of("Grade", "Present %", "Absent Days", "Leave Days", "Late Days"), num, rows, null, note);
            }
            case "STUDENT_WISE", "BELOW_THRESHOLD" -> {
                boolean below = "BELOW_THRESHOLD".equals(type);
                jdbc.query("select s.admission_no, s.full_name, coalesce(c.name, '') || ' – ' || coalesce(sc.name, ''), " + SUMS + " from school_att_marks m join students s on s.id = m.student_id left join school_classes c on c.id = s.school_class_id left join sections sc on sc.id = s.section_id" + where
                        + " group by s.id, s.admission_no, s.full_name, c.name, sc.name, c.grade_level order by coalesce(c.grade_level, -1), c.name, sc.name, s.full_name", (rs) -> {
                    Cnt c = cnt(rs, 4);
                    double r = c.rate(p);
                    if (below && r >= p.min()) return;
                    rows.add(List.of(rs.getString(1), rs.getString(2), rs.getString(3), String.valueOf(c.total()), String.valueOf(c.p()), String.valueOf(c.l()), String.valueOf(c.a()), String.valueOf(c.v()), r + "%", r < p.min() ? "Below " + p.min() + "%" : ""));
                }, args.toArray());
                num = List.of(false, false, false, true, true, true, true, true, true, false);
                return new Table(below ? "Students Below Attendance Threshold" : "Student-wise Attendance", sub + (below ? " · below " + p.min() + "%" : ""),
                        List.of("Admission No", "Student", "Grade / Section", "Days", "Present", "Late", "Absent", "On Leave", "Attendance %", "Flag"), num, rows, null, note);
            }
            default -> throw ApiException.badRequest("Choose a report type.");
        }
    }

    // ------------------------------------------------------------------ leave

    @Transactional(readOnly = true)
    public List<LeaveRow> leaves(String fromS, String toS, String status) {
        StringBuilder q = new StringBuilder("select l.id, s.id, s.full_name, s.admission_no, coalesce(c.name || ' – ' || sc.name, ''), l.leave_type, l.from_date, l.to_date, l.reason, l.status, l.decided_by from school_att_leaves l join students s on s.id = l.student_id left join school_classes c on c.id = s.school_class_id left join sections sc on sc.id = s.section_id where l.institution_id = ?");
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (fromS != null && !fromS.isBlank()) { q.append(" and l.to_date >= ?"); a.add(Date.valueOf(need(fromS, "from"))); }
        if (toS != null && !toS.isBlank()) { q.append(" and l.from_date <= ?"); a.add(Date.valueOf(need(toS, "to"))); }
        if (status != null && !status.isBlank()) { q.append(" and l.status = ?"); a.add(status.trim().toUpperCase(Locale.ROOT)); }
        q.append(" order by (l.status = 'PENDING') desc, l.from_date desc, l.id desc limit 300");
        return jdbc.query(q.toString(), (rs, n) -> new LeaveRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), label(LEAVE_TYPES, rs.getString(6)), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)), a.toArray());
    }

    public Map<String, Object> addLeave(LeaveBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        if (b.studentId() == null || count("select count(*) from students where id = ? and institution_id = ? and active", b.studentId(), inst()) == 0) err.put("studentId", "Choose a student.");
        LocalDate from = parse(b.from(), "from", err), to = parse(b.to(), "to", err);
        if (from != null && to != null) {
            if (to.isBefore(from)) err.put("to", "End date can't be before the start date.");
            else if (from.plusDays(MAX_LEAVE_DAYS).isBefore(to)) err.put("to", "Leave can be at most " + MAX_LEAVE_DAYS + " days.");
        }
        String type = b.type() == null || b.type().isBlank() ? "MEDICAL" : b.type().trim();
        if (!has(LEAVE_TYPES, type)) err.put("type", "Choose a leave type.");
        if (b.reason() != null && b.reason().trim().length() > 300) err.put("reason", "The reason can be at most 300 characters.");
        if (err.isEmpty() && count("select count(*) from school_att_leaves where student_id = ? and status in ('PENDING', 'APPROVED') and from_date <= ? and to_date >= ?", b.studentId(), Date.valueOf(to), Date.valueOf(from)) > 0)
            err.put("from", "This student already has leave that overlaps these dates.");
        fail(err);
        Long id = jdbc.queryForObject("insert into school_att_leaves (institution_id, student_id, leave_type, from_date, to_date, reason, created_by) values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                inst(), b.studentId(), type, Date.valueOf(from), Date.valueOf(to), clip(b.reason(), 300), actor());
        audit.record("STUDENT_LEAVE_ADDED", "STUDENT_LEAVE", id, inst(), "Student leave " + from + " to " + to);
        return Map.of("id", id);
    }

    private record Lv(long student, LocalDate from, LocalDate to, String status) {}
    private Lv leave(Long id) {
        List<Lv> l = jdbc.query("select student_id, from_date, to_date, status from school_att_leaves where id = ? and institution_id = ?", (rs, n) -> new Lv(rs.getLong(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(), rs.getString(4)), id, inst());
        if (l.isEmpty()) throw ApiException.notFound("That leave request wasn't found.");
        return l.get(0);
    }

    /** Approving writes On Leave marks for the school days in range; absent marks are converted, other marks are left alone. */
    public Map<String, Object> decide(Long id, String action) {
        Lv l = leave(id);
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        switch (act) {
            case "APPROVE" -> {
                if (!"PENDING".equals(l.status())) throw ApiException.conflict("Only pending requests can be approved.");
                Set<DayOfWeek> work = workingDays(); Map<LocalDate, String> hol = holidays(l.from(), l.to());
                int written = 0;
                for (LocalDate d = l.from(); !d.isAfter(l.to()); d = d.plusDays(1)) {
                    if (offReason(d, work, hol) != null) continue;
                    written += jdbc.update("insert into school_att_marks (institution_id, student_id, att_date, status, remarks, leave_id, marked_by) values (?, ?, ?, 'ON_LEAVE', 'Approved leave', ?, ?) "
                            + "on conflict (student_id, att_date) do update set status = 'ON_LEAVE', remarks = 'Approved leave', leave_id = excluded.leave_id, marked_by = excluded.marked_by, marked_at = now() where school_att_marks.status = 'ABSENT'",
                            inst(), l.student(), Date.valueOf(d), id, actor());
                }
                jdbc.update("update school_att_leaves set status = 'APPROVED', decided_by = ?, decided_at = now() where id = ?", actor(), id);
                audit.record("STUDENT_LEAVE_APPROVED", "STUDENT_LEAVE", id, inst(), "Student leave approved (" + written + " days marked)");
            }
            case "REJECT" -> {
                if (!"PENDING".equals(l.status())) throw ApiException.conflict("Only pending requests can be rejected.");
                jdbc.update("update school_att_leaves set status = 'REJECTED', decided_by = ?, decided_at = now() where id = ?", actor(), id);
                audit.record("STUDENT_LEAVE_REJECTED", "STUDENT_LEAVE", id, inst(), "Student leave rejected");
            }
            case "CANCEL" -> {
                if (!"APPROVED".equals(l.status()) && !"PENDING".equals(l.status())) throw ApiException.conflict("This request is already closed.");
                jdbc.update("delete from school_att_marks where leave_id = ?", id);
                jdbc.update("update school_att_leaves set status = 'CANCELLED', decided_by = ?, decided_at = now() where id = ?", actor(), id);
                audit.record("STUDENT_LEAVE_CANCELLED", "STUDENT_LEAVE", id, inst(), "Student leave cancelled");
            }
            default -> throw ApiException.badRequest("Unknown action.");
        }
        return Map.of("id", id);
    }

    // ------------------------------------------------------------------ trends

    @Transactional(readOnly = true)
    public Trends trends(Long termId, Long classId) {
        if (termId == null) throw ApiException.badRequest("Choose a term.");
        List<LocalDate[]> t = jdbc.query("select start_date, end_date from terms where id = ? and institution_id = ?", (rs, n) -> new LocalDate[] { rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate() }, termId, inst());
        if (t.isEmpty()) throw ApiException.notFound("That term wasn't found.");
        LocalDate from = t.get(0)[0], to = t.get(0)[1].isAfter(today()) ? today() : t.get(0)[1];
        Policy p = policy();
        if (to.isBefore(from)) return new Trends(0, null, 0, 0, p.min(), List.of());
        String cls = classId == null ? "" : " and s.school_class_id = ?";
        List<Object> a = new ArrayList<>(List.of(inst(), Date.valueOf(from), Date.valueOf(to)));
        if (classId != null) a.add(classId);
        record Key(long student, String month) {}
        Map<Key, Cnt> per = new HashMap<>();
        jdbc.query("select m.student_id, to_char(m.att_date, 'YYYY-MM'), " + SUMS + " from school_att_marks m join students s on s.id = m.student_id where s.institution_id = ? and m.att_date between ? and ?" + cls + " group by m.student_id, to_char(m.att_date, 'YYYY-MM')",
                (rs) -> { per.put(new Key(rs.getLong(1), rs.getString(2)), cnt(rs, 3)); }, a.toArray());
        Map<String, long[]> month = new TreeMap<>();   // month → p, l, a, v totals
        Map<String, Set<Long>> belowIn = new TreeMap<>();
        Map<Long, long[]> perStudent = new HashMap<>();
        per.forEach((k, c) -> {
            long[] m = month.computeIfAbsent(k.month(), (x) -> new long[4]);
            m[0] += c.p(); m[1] += c.l(); m[2] += c.a(); m[3] += c.v();
            long[] s = perStudent.computeIfAbsent(k.student(), (x) -> new long[4]);
            s[0] += c.p(); s[1] += c.l(); s[2] += c.a(); s[3] += c.v();
            if (c.rate(p) < p.min() && c.total() - (p.leave() ? 0 : c.v()) > 0) belowIn.computeIfAbsent(k.month(), (x) -> new HashSet<>()).add(k.student());
        });
        List<MonthRow> rows = new ArrayList<>();
        double lowest = 101; String lowestM = null; long tp = 0, tl = 0, ta = 0, tv = 0;
        for (Map.Entry<String, long[]> e : month.entrySet()) {
            long[] m = e.getValue();
            Cnt c = new Cnt(m[0], m[1], m[2], m[3]);
            double r = c.rate(p);
            String label = YearMonth.parse(e.getKey()).getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + e.getKey().substring(0, 4);
            rows.add(new MonthRow(label, r, belowIn.getOrDefault(e.getKey(), Set.of()).size()));
            if (r < lowest) { lowest = r; lowestM = label; }
            tp += m[0]; tl += m[1]; ta += m[2]; tv += m[3];
        }
        long chronic = perStudent.values().stream().filter((s) -> { Cnt c = new Cnt(s[0], s[1], s[2], s[3]); return c.total() - (p.leave() ? 0 : c.v()) > 0 && c.rate(p) < p.min(); }).count();
        return new Trends(new Cnt(tp, tl, ta, tv).rate(p), lowestM, rows.isEmpty() ? 0 : lowest, chronic, p.min(), rows);
    }

    // ------------------------------------------------------------------ calendar

    @Transactional(readOnly = true)
    public Calendar calendar(Long studentId, String monthS) {
        if (studentId == null) throw ApiException.badRequest("Choose a student.");
        List<String[]> st = jdbc.query("select s.full_name, coalesce(c.name || ' – ' || sc.name, '') from students s left join school_classes c on c.id = s.school_class_id left join sections sc on sc.id = s.section_id where s.id = ? and s.institution_id = ?", (rs, n) -> new String[] { rs.getString(1), rs.getString(2) }, studentId, inst());
        if (st.isEmpty()) throw ApiException.notFound("That student wasn't found.");
        YearMonth ym;
        try { ym = monthS == null || monthS.isBlank() ? YearMonth.from(today()) : YearMonth.parse(monthS.trim()); } catch (DateTimeParseException e) { throw ApiException.badRequest("Choose a valid month."); }
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        Map<LocalDate, String> marks = new HashMap<>();
        jdbc.query("select att_date, status from school_att_marks where student_id = ? and att_date between ? and ?", (rs) -> { marks.put(rs.getDate(1).toLocalDate(), rs.getString(2)); }, studentId, Date.valueOf(from), Date.valueOf(to));
        Set<DayOfWeek> work = workingDays(); Map<LocalDate, String> hol = holidays(from, to);
        List<CalDay> days = new ArrayList<>();
        long p = 0, a = 0, l = 0, v = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String s = marks.get(d);
            if (s != null) { switch (s) { case "PRESENT" -> p++; case "LATE" -> l++; case "ABSENT" -> a++; default -> v++; } }
            days.add(new CalDay(d.toString(), s, offReason(d, work, hol)));
        }
        return new Calendar(st.get(0)[0], st.get(0)[1], ym.toString(), days, p, a, l, v);
    }
}
