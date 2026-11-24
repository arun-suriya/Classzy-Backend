package com.centram.classzy.school;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
import com.centram.classzy.school.SchoolDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * School Admin dashboard and the records behind it. Every query is limited to the signed-in user's institution
 * (CurrentUser.institutionId(): from the token for a school admin, from the header for a Super Admin previewing a school).
 */
@Service
@Transactional
public class SchoolService {

    static final List<String[]> STAGES = List.of(new String[] { "SUBMITTED", "Submitted" }, new String[] { "UNDER_REVIEW", "Under Review" }, new String[] { "DOCUMENTS_PENDING", "Documents Pending" },
            new String[] { "ASSESSMENT", "Assessment" }, new String[] { "INTERVIEW", "Interview" }, new String[] { "WAITLISTED", "Waitlisted" }, new String[] { "OFFER_ISSUED", "Offer Issued" },
            new String[] { "ADMITTED", "Admitted" }, new String[] { "REJECTED", "Rejected" }, new String[] { "WITHDRAWN", "Withdrawn" });
    static final Set<String> CATEGORIES = Set.of("MEETING", "OBSERVATION", "PARENT", "ADMISSION", "REVIEW", "OTHER");
    static final Set<String> LEAVE_TYPES = Set.of("CASUAL", "SICK", "EARNED", "MEDICAL", "OTHER");
    static final Set<String> METHODS = Set.of("CASH", "UPI", "CARD", "CHEQUE", "BANK_TRANSFER", "ONLINE");
    static final DateTimeFormatter TIME_12 = DateTimeFormatter.ofPattern("hh:mm a");

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    private final com.centram.classzy.school.fees.FeeService fees;
    private final com.centram.classzy.school.comm.NotifyService notify;

    public SchoolService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, com.centram.classzy.school.fees.FeeService fees, com.centram.classzy.school.comm.NotifyService notify) {
        this.notify = notify;
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
        this.fees = fees;
    }

    private static String iso(java.sql.Timestamp t) { return t == null ? null : t.toInstant().toString(); }
    private static Integer pct(long cur, long prev) { return prev == 0 ? null : (int) Math.round((cur - prev) * 100.0 / prev); }
    private long count(String sql, Object... args) { Long n = jdbc.queryForObject(sql, Long.class, args); return n == null ? 0 : n; }
    private double sum(String sql, Object... args) { BigDecimal n = jdbc.queryForObject(sql, BigDecimal.class, args); return n == null ? 0 : n.doubleValue(); }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long yearId, Long termId) {
        long inst = current.institutionId();
        String schoolName = jdbc.query("select name from institutions where id = ?", (rs, i) -> rs.getString(1), inst).stream().findFirst().orElseThrow(() -> ApiException.notFound("School"));
        boolean hasLogo = count("select count(*) from institution_images where institution_id = ? and kind = 'LOGO'", inst) > 0;
        String userName = jdbc.query("select full_name from users where id = ?", (rs, i) -> rs.getString(1), current.principal().userId()).stream().findFirst().orElse("Admin");

        List<Pick> years = jdbc.query("select id, name, is_current from academic_years where institution_id = ? and active order by start_date desc", (rs, i) -> new Pick(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), inst);
        if (yearId != null) {
            final Long wanted = yearId;
            if (years.stream().noneMatch(p -> p.id() == wanted)) throw ApiException.badRequest("That academic year isn't part of this school.");
        }
        final Long y = yearId != null ? yearId : years.stream().filter(Pick::current).map(Pick::id).findFirst().orElse(years.isEmpty() ? null : years.get(0).id());
        List<Pick> terms = y == null ? List.of() : jdbc.query("select id, name from terms where academic_year_id = ? and institution_id = ? and active order by start_date", (rs, i) -> new Pick(rs.getLong(1), rs.getString(2), false), y, inst);
        Long pickedTerm = termId;
        if (pickedTerm != null) {
            final Long wantedTerm = pickedTerm;
            if (terms.stream().noneMatch(p -> p.id() == wantedTerm)) pickedTerm = null;
        }
        final Long t = pickedTerm;

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(11).withDayOfMonth(1);
        LocalDate scopeStart = null;
        String scopeLabel = "No academic year set up";
        if (y != null) {
            Map<String, Object> row = jdbc.queryForMap("select start_date, end_date, name from academic_years where id = ?", y);
            scopeStart = ((Date) row.get("start_date")).toLocalDate();
            scopeLabel = "Academic Year " + row.get("name");
            if (t != null) {
                Map<String, Object> tr = jdbc.queryForMap("select start_date, name from terms where id = ?", t);
                scopeStart = ((Date) tr.get("start_date")).toLocalDate();
                scopeLabel += " · " + tr.get("name");
            }
            start = ((Date) row.get("start_date")).toLocalDate().withDayOfMonth(1);
        }

        // ---- cards
        long students = count("select count(*) from students where institution_id = ? and active", inst);
        Integer studentChange = null;
        String studentCompare = "since term start";
        if (scopeStart != null) {
            long before = count("select count(*) from students where institution_id = ? and active and created_at < ?", inst, java.sql.Timestamp.valueOf(scopeStart.atStartOfDay()));
            studentChange = pct(students, before);
            if (t == null) studentCompare = "since year start";
        }
        long staff = count("select count(*) from teachers where institution_id = ? and active", inst) + count("select count(*) from users where institution_id = ? and user_type = 'STAFF' and status = 'ACTIVE'", inst);
        long apps30 = count("select count(*) from school_applications where institution_id = ? and submitted_at >= now() - interval '30 days'", inst);
        long apps60 = count("select count(*) from school_applications where institution_id = ? and submitted_at >= now() - interval '60 days' and submitted_at < now() - interval '30 days'", inst);
        long openEnq = count("select count(*) from school_enquiries where institution_id = ? and status in ('NEW','FOLLOW_UP')", inst);
        long enq30 = count("select count(*) from school_enquiries where institution_id = ? and created_at >= now() - interval '30 days'", inst);
        long enq60 = count("select count(*) from school_enquiries where institution_id = ? and created_at >= now() - interval '60 days' and created_at < now() - interval '30 days'", inst);

        // ---- fees, limited to the selected year (and term, when one is chosen)
        String scope = " r.institution_id = ? and r.academic_year_id = ? " + (t != null ? " and (r.term_id = ? or r.term_id is null) " : "");
        Object[] sa = t != null ? new Object[] { inst, y, t } : new Object[] { inst, y };
        double billed = 0, collected = 0, pending = 0, col30 = 0, col60 = 0;
        if (y != null) {
            billed = sum("select coalesce(sum(r.net),0) from school_invoice_v r where" + scope, sa);
            collected = sum("select coalesce(sum(r.paid),0) from school_invoice_v r where" + scope, sa);
            pending = sum("select coalesce(sum(greatest(r.balance,0)),0) from school_invoice_v r where" + scope, sa);
            col30 = sum("select coalesce(sum(p.amount),0) from school_fee_payments p join school_fee_records r on r.id = p.fee_record_id where" + scope + " and p.paid_on > current_date - 30", sa);
            col60 = sum("select coalesce(sum(p.amount),0) from school_fee_payments p join school_fee_records r on r.id = p.fee_record_id where" + scope + " and p.paid_on > current_date - 60 and p.paid_on <= current_date - 30", sa);
        }
        int percent = billed <= 0 ? 0 : (int) Math.min(100, Math.round(collected * 100 / billed));

        // ---- enrollment trend (one point per month; future months stay empty)
        List<Month> trend = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate m = start.plusMonths(i);
            if (m.isAfter(today)) { trend.add(new Month(m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH), null)); continue; }
            long n = count("select count(*) from students where institution_id = ? and active and created_at < ?", inst, java.sql.Timestamp.valueOf(m.plusMonths(1).atStartOfDay()));
            trend.add(new Month(m.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH), (int) n));
        }

        // ---- applications by stage
        Map<String, Long> byStage = new LinkedHashMap<>();
        jdbc.query("select stage, count(*) from school_applications where institution_id = ? group by stage", rs -> { byStage.put(rs.getString(1), rs.getLong(2)); }, inst);
        List<Stage> stages = STAGES.stream().map(s -> new Stage(s[0], s[1], byStage.getOrDefault(s[0], 0L))).toList();
        long appsTotal = stages.stream().mapToLong(Stage::count).sum();

        // ---- today's schedule
        List<Event> schedule = jdbc.query("select id, start_time, title, location, category from school_events where institution_id = ? and event_date <= current_date and coalesce(end_date, event_date) >= current_date order by start_time nulls first, id",
                (rs, i) -> { Time tm = rs.getTime(2); return new Event(rs.getLong(1), tm == null ? "All day" : tm.toLocalTime().format(TIME_12), rs.getString(3), rs.getString(4), rs.getString(5)); }, inst);

        // ---- pending actions
        List<PendingItem> pend = List.of(
                new PendingItem("applications", "Review pending admission applications", count("select count(*) from school_applications where institution_id = ? and stage in ('SUBMITTED','UNDER_REVIEW')", inst)),
                new PendingItem("concessions", "Approve fee concession requests", count("select count(*) from school_fee_concessions where institution_id = ? and status = 'PENDING'", inst)),
                new PendingItem("documents", "Verify documents (admissions)", count("select count(*) from school_applications where institution_id = ? and stage = 'DOCUMENTS_PENDING'", inst)),
                new PendingItem("enquiries", "Resolve parent enquiries", count("select count(*) from school_enquiries where institution_id = ? and status = 'NEW'", inst)),
                new PendingItem("leave", "Approve staff leave requests", count("select count(*) from school_leave_requests where institution_id = ? and status = 'PENDING'", inst)));
        long pendingTotal = pend.stream().mapToLong(PendingItem::count).sum();

        // ---- recent activity: this school's own audit trail
        List<Activity> acts = jdbc.query("select created_at, description, action from audit_logs where institution_id = ? order by created_at desc, id desc limit 6", (rs, i) -> new Activity(iso(rs.getTimestamp(1)), rs.getString(2), rs.getString(3)), inst);

        return new Dashboard(inst, schoolName, hasLogo, userName, years, terms, y, t, scopeLabel,
                new Metric(students, studentChange, studentCompare), new Metric(staff, null, null), new Metric(apps30, pct(apps30, apps60), "vs last month"),
                new Metric(collected, pct(Math.round(col30), Math.round(col60)), "vs last month"), new Metric(pending, null, null), new Metric(openEnq, pct(enq30, enq60), "new enquiries vs last month"),
                trend, stages, appsTotal, new Fees(collected, pending, billed, percent), schedule, pend, pendingTotal, acts);
    }

    // ------------------------------------------------------------------ calendar

    private static String clip(String v, int max) { return v == null ? null : v.trim().isEmpty() ? null : (v.trim().length() > max ? v.trim().substring(0, max) : v.trim()); }
    private static LocalDate date(String v, String key, Map<String, String> err, boolean required) {
        if (v == null || v.isBlank()) { if (required) err.put(key, "Required."); return null; }
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { err.put(key, "Use a date like 2026-10-12."); return null; }
    }
    private static String cleanKey(String v, Set<String> allowed, String def, String key, Map<String, String> err) {
        String s = v == null || v.isBlank() ? def : v.trim().toUpperCase();
        if (!allowed.contains(s)) { err.put(key, "Choose one of the listed options."); return def; }
        return s;
    }
    private static void fail(Map<String, String> err) { if (!err.isEmpty()) throw new FieldErrorsException(err); }
    private String actor() { return current.principal().email(); }

    public Event addEvent(EventBody b) {
        long inst = current.institutionId();
        Map<String, String> err = new LinkedHashMap<>();
        String title = clip(b.title(), 150);
        if (title == null) err.put("title", "Required.");
        LocalDate d = date(b.date(), "date", err, true);
        LocalTime tm = null;
        if (b.time() != null && !b.time().isBlank()) { try { tm = LocalTime.parse(b.time().trim()); } catch (DateTimeParseException e) { err.put("time", "Use the HH:mm format."); } }
        String cat = cleanKey(b.category(), CATEGORIES, "OTHER", "category", err);
        fail(err);
        Long id = jdbc.queryForObject("insert into school_events (institution_id, title, event_date, end_date, start_time, location, category, created_by) values (?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                inst, title, Date.valueOf(d), Date.valueOf(d), tm == null ? null : Time.valueOf(tm), clip(b.location(), 100), cat, actor());
        audit.record("SCHOOL_EVENT_CREATED", "SCHOOL_EVENT", id, inst, "Added \"" + title + "\" to the calendar");
        return new Event(id, tm == null ? "All day" : tm.format(TIME_12), title, clip(b.location(), 100), cat);
    }

    public void deleteEvent(Long id) {
        long inst = current.institutionId();
        if (jdbc.update("delete from school_events where id = ? and institution_id = ?", id, inst) == 0) throw ApiException.notFound("Event");
        audit.record("SCHOOL_EVENT_DELETED", "SCHOOL_EVENT", id, inst, "Removed a calendar event");
    }

    // ------------------------------------------------------------------ leave & concessions

    public Map<String, Object> addLeave(LeaveBody b) {
        long inst = current.institutionId();
        Map<String, String> err = new LinkedHashMap<>();
        if (b.teacherId() == null || count("select count(*) from teachers where id = ? and institution_id = ?", b.teacherId(), inst) == 0) err.put("teacherId", "Choose a staff member from this school.");
        LocalDate from = date(b.from(), "from", err, true), to = date(b.to(), "to", err, true);
        if (from != null && to != null && to.isBefore(from)) err.put("to", "End date can't be before the start date.");
        String type = cleanKey(b.leaveType(), LEAVE_TYPES, "CASUAL", "leaveType", err);
        fail(err);
        Long id = jdbc.queryForObject("insert into school_leave_requests (institution_id, teacher_id, leave_type, from_date, to_date, reason) values (?, ?, ?, ?, ?, ?) returning id", Long.class,
                inst, b.teacherId(), type, Date.valueOf(from), Date.valueOf(to), clip(b.reason(), 300));
        String who = jdbc.queryForObject("select full_name from teachers where id = ?", String.class, b.teacherId());
        audit.record("LEAVE_REQUESTED", "LEAVE", id, inst, "Leave request submitted - " + who);
        notify.add(inst, "STAFF", "Leave request", who + " requested leave (" + from + " to " + to + ").", "/school/dashboard");
        return Map.of("id", id);
    }

    @Transactional(readOnly = true)
    public List<LeaveRow> pendingLeave() {
        return jdbc.query("select l.id, t.full_name, l.leave_type, l.from_date, l.to_date, l.reason, l.status from school_leave_requests l join teachers t on t.id = l.teacher_id where l.institution_id = ? and l.status = 'PENDING' order by l.created_at",
                (rs, i) -> new LeaveRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)), current.institutionId());
    }

    public Map<String, Object> decideLeave(Long id, Boolean approve) {
        long inst = current.institutionId();
        if (approve == null) throw ApiException.badRequest("Choose approve or reject.");
        String st = approve ? "APPROVED" : "REJECTED";
        if (jdbc.update("update school_leave_requests set status = ?, decided_by = ?, decided_at = now() where id = ? and institution_id = ? and status = 'PENDING'", st, actor(), id, inst) == 0) throw ApiException.conflict("That request was already decided or doesn't exist.");
        String who = jdbc.queryForObject("select t.full_name from school_leave_requests l join teachers t on t.id = l.teacher_id where l.id = ?", String.class, id);
        audit.record("LEAVE_" + st, "LEAVE", id, inst, "Leave " + st.toLowerCase() + " - " + who);
        return Map.of("id", id, "status", st);
    }

    @Transactional(readOnly = true)
    public List<ConcessionRow> pendingConcessions() {
        return jdbc.query("select c.id, s.full_name, s.admission_no, c.amount, c.reason, c.status from school_fee_concessions c join students s on s.id = c.student_id where c.institution_id = ? and c.status = 'PENDING' order by c.requested_at",
                (rs, i) -> new ConcessionRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4).doubleValue(), rs.getString(5), rs.getString(6)), current.institutionId());
    }

    /** Decisions go through FeeService so the balance rules apply the same way here and on the Fees page. */
    public Map<String, Object> decideConcession(Long id, Boolean approve) { return fees.decideConcession(id, approve); }
}
