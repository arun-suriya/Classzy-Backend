package com.centram.classzy.school.comm;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import com.centram.classzy.school.comm.MsgDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Messages, templates, notifications and announcements for one school.
 * No email/SMS/WhatsApp provider is connected: sending records the message and who it was addressed to, it does not deliver it.
 * Announcements are records too; there is no parent or student portal to show them in yet.
 */
@Service
@Transactional
public class MsgService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final List<Code> AUDIENCES = List.of(new Code("ALL_PARENTS", "All Parents"), new Code("SELECTED_CLASS", "Selected Class"), new Code("ALL_STUDENTS", "All Students"), new Code("STAFF", "Staff"));
    static final List<Code> CHANNELS = List.of(new Code("EMAIL", "Email"), new Code("SMS", "SMS"), new Code("WHATSAPP", "WhatsApp"), new Code("IN_APP", "In-app"));
    static final List<Code> CATEGORIES = List.of(new Code("GENERAL", "General"), new Code("FEES", "Fees"), new Code("ACADEMIC", "Academic"), new Code("EVENTS", "Events"), new Code("ATTENDANCE", "Attendance"), new Code("ADMISSIONS", "Admissions"));
    static final List<Code> ANN_AUDIENCES = List.of(new Code("EVERYONE", "Everyone"), new Code("PARENTS", "All Parents"), new Code("STUDENTS", "All Students"), new Code("STAFF", "Staff"));
    static final int MAX_RECIPIENTS = 5000;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;
    private final NotifyService notify;

    public MsgService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, NotifyService notify) {
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
        this.notify = notify;
    }

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }
    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static String clip(String v, int max) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t); }
    private static boolean has(List<Code> l, String c) { return l.stream().anyMatch((x) -> x.code().equals(c)); }
    private static String up(String v) { return v == null ? "" : v.trim().toUpperCase(); }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }
    private static String like(String q) { return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        List<ClassOpt> classes = jdbc.query("select c.id, c.name from school_classes c join academic_years y on y.id = c.academic_year_id where c.institution_id = ? and c.active and y.is_current order by coalesce(c.grade_level, -1), c.name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getString(2)), i);
        List<SecOpt> secs = jdbc.query("select s.id, s.school_class_id, s.name from sections s join school_classes c on c.id = s.school_class_id join academic_years y on y.id = c.academic_year_id where s.institution_id = ? and s.active and y.is_current order by s.name", (rs, n) -> new SecOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i);
        return new Options(classes, secs, AUDIENCES, CHANNELS, CATEGORIES, ANN_AUDIENCES);
    }

    // ------------------------------------------------------------------ recipients

    private record Aud(String kind, Long classId, Long sectionId, String label) {}
    private record Rcpt(String name, String student, String contact, boolean reachable) {}

    private Aud audience(String kind, Long classId, Long sectionId, Map<String, String> e) {
        String k = up(kind);
        if (!has(AUDIENCES, k)) { e.put("audience", "Choose who to send to."); return null; }
        if (!k.equals("SELECTED_CLASS")) return new Aud(k, null, null, AUDIENCES.stream().filter((a) -> a.code().equals(k)).findFirst().get().label());
        if (classId == null) { e.put("classId", "Choose a grade."); return null; }
        String cn = jdbc.query("select name from school_classes where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), classId, inst()).stream().findFirst().orElse(null);
        if (cn == null) { e.put("classId", "Choose a grade from the list."); return null; }
        if (sectionId == null) return new Aud(k, classId, null, cn);
        String sn = jdbc.query("select name from sections where id = ? and institution_id = ? and school_class_id = ?", (rs, n) -> rs.getString(1), sectionId, inst(), classId).stream().findFirst().orElse(null);
        if (sn == null) { e.put("sectionId", "Choose a section of that grade."); return null; }
        return new Aud(k, classId, sectionId, cn + " – " + sn);
    }

    private List<Rcpt> resolve(long institutionId, String kind, Long classId, Long sectionId, String channel) {
        boolean email = channel.equals("EMAIL"), inApp = channel.equals("IN_APP");
        List<Rcpt> out = new ArrayList<>();
        if (kind.equals("STAFF")) {
            jdbc.query("select full_name, email, phone from teachers where institution_id = ? and active order by full_name", (rs) -> { String c = email ? rs.getString(2) : rs.getString(3); out.add(new Rcpt(rs.getString(1), null, c, inApp || (c != null && !c.isBlank()))); }, institutionId);
            return out;
        }
        StringBuilder q = new StringBuilder("select full_name, guardian_name, guardian_email, guardian_phone from students where institution_id = ? and active");
        List<Object> a = new ArrayList<>(List.of(institutionId));
        if (kind.equals("SELECTED_CLASS")) { q.append(" and school_class_id = ?"); a.add(classId); if (sectionId != null) { q.append(" and section_id = ?"); a.add(sectionId); } }
        q.append(" order by full_name, id");
        boolean students = kind.equals("ALL_STUDENTS");
        jdbc.query(q.toString(), (rs) -> {
            String c = email ? rs.getString(3) : rs.getString(4);
            String guardian = rs.getString(2);
            out.add(new Rcpt(students ? rs.getString(1) : (guardian == null || guardian.isBlank() ? "Guardian of " + rs.getString(1) : guardian), students ? null : rs.getString(1), c, inApp || (c != null && !c.isBlank())));
        }, a.toArray());
        return out;
    }

    @Transactional(readOnly = true)
    public Preview preview(String kind, Long classId, Long sectionId, String channel) {
        Map<String, String> e = new LinkedHashMap<>();
        Aud a = audience(kind, classId, sectionId, e);
        String ch = up(channel);
        if (a == null || !has(CHANNELS, ch)) return new Preview(0, 0);
        List<Rcpt> r = resolve(inst(), a.kind(), a.classId(), a.sectionId(), ch);
        return new Preview(r.size(), (int) r.stream().filter(Rcpt::reachable).count());
    }

    // ------------------------------------------------------------------ send / schedule

    private static MsgRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MsgRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), iso(rs.getTimestamp(6)), iso(rs.getTimestamp(7)), rs.getInt(8), rs.getInt(9), rs.getString(10), rs.getString(11));
    }
    private static final String COLS = "select id, subject, audience_label, channel, status, sent_at, scheduled_at, recipient_count, reachable_count, note, created_by from school_messages";

    public Map<String, Object> send(SendBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        Aud a = audience(b.audience(), b.classId(), b.sectionId(), e);
        String ch = up(b.channel());
        if (!has(CHANNELS, ch)) e.put("channel", "Choose a channel.");
        String subject = clip(b.subject(), 200);
        if (subject == null) e.put("subject", "Required.");
        String body = Html.clean(b.body());
        if (Html.text(body).isEmpty()) e.put("body", "Write a message.");
        else if (Html.text(body).length() > 10000) e.put("body", "That message is too long (10,000 characters at most).");
        ZonedDateTime when = null;
        if (b.scheduleDate() != null && !b.scheduleDate().isBlank()) {
            try { when = ZonedDateTime.of(LocalDate.parse(b.scheduleDate().trim()), LocalTime.parse(b.scheduleTime() == null || b.scheduleTime().isBlank() ? "09:00" : b.scheduleTime().trim()), IST); }
            catch (DateTimeParseException x) { e.put("scheduleDate", "Choose a valid date and time."); }
            if (when != null && when.isBefore(ZonedDateTime.now(IST).plusMinutes(1))) e.put("scheduleDate", "Choose a time in the future.");
            if (when != null && when.isAfter(ZonedDateTime.now(IST).plusYears(1))) e.put("scheduleDate", "Schedule within the next year.");
        }
        fail(e);
        if (when != null) {
            Long id = jdbc.queryForObject("insert into school_messages (institution_id, audience_kind, class_id, section_id, audience_label, channel, subject, body, status, scheduled_at, created_by) values (?,?,?,?,?,?,?,?, 'SCHEDULED', ?, ?) returning id", Long.class,
                    i, a.kind(), a.classId(), a.sectionId(), a.label(), ch, subject, body, Timestamp.from(when.toInstant()), actor());
            audit.record("MESSAGE_SCHEDULED", "MESSAGE", id, i, "Message \"" + subject + "\" scheduled for " + a.label());
            return Map.of("id", id, "status", "SCHEDULED");
        }
        List<Rcpt> r = resolve(i, a.kind(), a.classId(), a.sectionId(), ch);
        if (r.isEmpty()) throw new FieldErrorsException(Map.of("audience", "Nobody matches that audience."));
        if (r.size() > MAX_RECIPIENTS) throw new FieldErrorsException(Map.of("audience", "That's " + r.size() + " recipients. Narrow it to " + MAX_RECIPIENTS + " or fewer."));
        Long id = jdbc.queryForObject("insert into school_messages (institution_id, audience_kind, class_id, section_id, audience_label, channel, subject, body, status, sent_at, created_by) values (?,?,?,?,?,?,?,?, 'SENT', now(), ?) returning id", Long.class,
                i, a.kind(), a.classId(), a.sectionId(), a.label(), ch, subject, body, actor());
        int reach = store(id, r);
        audit.record("MESSAGE_SENT", "MESSAGE", id, i, "Message \"" + subject + "\" recorded for " + r.size() + " recipients (" + a.label() + ")");
        return Map.of("id", id, "status", "SENT", "recipients", r.size(), "reachable", reach);
    }

    private int store(long messageId, List<Rcpt> r) {
        jdbc.batchUpdate("insert into school_message_recipients (message_id, name, student_name, contact, reachable) values (?,?,?,?,?)", r, 500, (ps, x) -> { ps.setLong(1, messageId); ps.setString(2, x.name()); ps.setString(3, x.student()); ps.setString(4, x.contact()); ps.setBoolean(5, x.reachable()); });
        int reach = (int) r.stream().filter(Rcpt::reachable).count();
        jdbc.update("update school_messages set recipient_count = ?, reachable_count = ? where id = ?", r.size(), reach, messageId);
        return reach;
    }

    public void cancel(Long id) {
        long i = inst();
        String subject = jdbc.query("select subject from school_messages where id = ? and institution_id = ? and status = 'SCHEDULED'", (rs, n) -> rs.getString(1), id, i).stream().findFirst().orElseThrow(() -> ApiException.conflict("Only scheduled messages can be cancelled."));
        jdbc.update("update school_messages set status = 'CANCELLED' where id = ?", id);
        audit.record("MESSAGE_CANCELLED", "MESSAGE", id, i, "Scheduled message \"" + subject + "\" cancelled");
    }

    /** Called every minute by MessageDispatcher: records scheduled messages whose time has come. No CurrentUser, so everything is keyed by the message's own school. */
    public int dispatchDue() {
        List<Object[]> due = jdbc.query("select id, institution_id, audience_kind, class_id, section_id, channel, subject from school_messages where status = 'SCHEDULED' and scheduled_at <= now() order by scheduled_at limit 50 for update skip locked",
                (rs, n) -> new Object[] { rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getObject(4) == null ? null : rs.getLong(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getString(6), rs.getString(7) });
        for (Object[] m : due) {
            long id = (Long) m[0], school = (Long) m[1];
            List<Rcpt> r = resolve(school, (String) m[2], (Long) m[3], (Long) m[4], (String) m[5]);
            if (r.isEmpty() || r.size() > MAX_RECIPIENTS) {
                jdbc.update("update school_messages set status = 'CANCELLED', note = ? where id = ?", r.isEmpty() ? "Nobody matched the audience when it was due." : "Too many recipients when it was due.", id);
                notify.add(school, "COMMUNICATION", "Scheduled message not sent", "\"" + m[6] + "\" was cancelled: " + (r.isEmpty() ? "nobody matched the audience." : "too many recipients."), "/school/communication?tab=Sent");
                continue;
            }
            jdbc.update("update school_messages set status = 'SENT', sent_at = now() where id = ?", id);
            store(id, r);
            notify.add(school, "COMMUNICATION", "Scheduled message recorded", "\"" + m[6] + "\" was recorded for " + r.size() + " recipients.", "/school/communication?tab=Sent");
        }
        jdbc.update("delete from school_notifications where created_at < now() - interval '90 days'");
        return due.size();
    }

    @Transactional(readOnly = true)
    public MsgPage messages(String from, String to, String channel, String status, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where institution_id = ?");
        try {
            if (from != null && !from.isBlank()) { w.append(" and (coalesce(sent_at, scheduled_at, created_at) at time zone 'Asia/Kolkata')::date >= ?"); a.add(Date.valueOf(LocalDate.parse(from.trim()))); }
            if (to != null && !to.isBlank()) { w.append(" and (coalesce(sent_at, scheduled_at, created_at) at time zone 'Asia/Kolkata')::date <= ?"); a.add(Date.valueOf(LocalDate.parse(to.trim()))); }
        } catch (DateTimeParseException x) { throw ApiException.badRequest("Use dates like 2026-11-30."); }
        if (channel != null && !channel.isBlank()) { w.append(" and channel = ?"); a.add(up(channel)); }
        if (status != null && !status.isBlank()) { w.append(" and status = ?"); a.add(up(status)); }
        long total = count("select count(*) from school_messages" + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        return new MsgPage(jdbc.query(COLS + w + " order by coalesce(sent_at, scheduled_at, created_at) desc, id desc limit ? offset ?", (rs, n) -> row(rs), a.toArray()), total, pg, sz);
    }

    @Transactional(readOnly = true)
    public MsgDetail message(Long id) {
        long i = inst();
        MsgRow r = jdbc.query(COLS + " where id = ? and institution_id = ?", (rs, n) -> row(rs), id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Message"));
        String body = jdbc.queryForObject("select body from school_messages where id = ?", String.class, id);
        List<Recipient> rc = jdbc.query("select name, student_name, contact, reachable from school_message_recipients where message_id = ? order by id limit 201", (rs, n) -> new Recipient(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), id);
        boolean more = rc.size() > 200;
        return new MsgDetail(r, body, more ? rc.subList(0, 200) : rc, more);
    }

    // ------------------------------------------------------------------ templates

    @Transactional(readOnly = true)
    public List<TplRow> templates(String q, String category) {
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where institution_id = ?");
        if (q != null && !q.isBlank()) { w.append(" and (lower(name) like lower(?) escape '\\' or lower(subject) like lower(?) escape '\\')"); a.add(like(q)); a.add(like(q)); }
        if (category != null && !category.isBlank()) { w.append(" and category = ?"); a.add(up(category)); }
        return jdbc.query("select id, name, category, channel, subject, body from school_msg_templates" + w + " order by name", (rs, n) -> new TplRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), a.toArray());
    }

    private String[] checkTpl(TplBody b, Long selfId) {
        Map<String, String> e = new LinkedHashMap<>();
        String name = clip(b.name(), 100);
        if (name == null) e.put("name", "Required.");
        String cat = up(b.category()), ch = up(b.channel());
        if (!has(CATEGORIES, cat)) e.put("category", "Choose a category.");
        if (!has(CHANNELS, ch)) e.put("channel", "Choose a channel.");
        String subject = clip(b.subject(), 200);
        if (subject == null) e.put("subject", "Required.");
        String body = Html.clean(b.body());
        if (Html.text(body).isEmpty()) e.put("body", "Write the message.");
        if (name != null && count("select count(*) from school_msg_templates where institution_id = ? and lower(name) = lower(?) and id <> ?", inst(), name, selfId == null ? -1L : selfId) > 0) e.put("name", "A template with that name already exists.");
        fail(e);
        return new String[] { name, cat, ch, subject, body };
    }

    public Map<String, Object> addTemplate(TplBody b) {
        String[] t = checkTpl(b, null);
        Long id = jdbc.queryForObject("insert into school_msg_templates (institution_id, name, category, channel, subject, body, created_by) values (?,?,?,?,?,?,?) returning id", Long.class, inst(), t[0], t[1], t[2], t[3], t[4], actor());
        audit.record("MSG_TEMPLATE_ADDED", "MSG_TEMPLATE", id, inst(), "Message template " + t[0] + " added");
        return Map.of("id", id);
    }

    public Map<String, Object> updateTemplate(Long id, TplBody b) {
        if (count("select count(*) from school_msg_templates where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("Template");
        String[] t = checkTpl(b, id);
        jdbc.update("update school_msg_templates set name = ?, category = ?, channel = ?, subject = ?, body = ? where id = ?", t[0], t[1], t[2], t[3], t[4], id);
        audit.record("MSG_TEMPLATE_UPDATED", "MSG_TEMPLATE", id, inst(), "Message template " + t[0] + " updated");
        return Map.of("id", id);
    }

    public void deleteTemplate(Long id) {
        String name = jdbc.query("select name from school_msg_templates where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Template"));
        jdbc.update("delete from school_msg_templates where id = ?", id);
        audit.record("MSG_TEMPLATE_DELETED", "MSG_TEMPLATE", id, inst(), "Message template " + name + " deleted");
    }

    /** Adds a few ready-made templates the school can edit. Existing names are left alone. */
    public Map<String, Object> starterTemplates() {
        long i = inst();
        String[][] s = {
            { "Fee Reminder", "FEES", "EMAIL", "Fee reminder", "<p>Dear Parents,</p><p>This is a gentle reminder that the fee for your ward is due. Please clear the balance at the earliest. If you have already paid, kindly ignore this message.</p>" },
            { "Parent Meeting", "ACADEMIC", "EMAIL", "Parent Meeting", "<p>Dear Parents,</p><p>There will be a Parent Meeting on [date] to discuss the upcoming academic plan and events. Your presence is requested.</p>" },
            { "Holiday Notice", "EVENTS", "SMS", "School holiday", "<p>Dear Parents, the school will remain closed on [date] on account of [occasion]. Classes resume on [date].</p>" },
            { "Absence Alert", "ATTENDANCE", "SMS", "Absence alert", "<p>Dear Parent, your ward was absent from school today. Please send a note or contact the class teacher.</p>" } };
        int added = 0;
        for (String[] t : s) added += jdbc.update("insert into school_msg_templates (institution_id, name, category, channel, subject, body, created_by) values (?,?,?,?,?,?,?) on conflict (institution_id, lower(name)) do nothing", i, t[0], t[1], t[2], t[3], t[4], actor());
        return Map.of("added", added);
    }

    // ------------------------------------------------------------------ notifications

    @Transactional(readOnly = true)
    public long unread() {
        return count("select count(*) from school_notifications n where n.institution_id = ? and not exists (select 1 from school_notification_reads r where r.notification_id = n.id and r.user_id = ?)", inst(), current.principal().userId());
    }

    @Transactional(readOnly = true)
    public NotifPage notifications(String kind, boolean unreadOnly, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        Long uid = current.principal().userId();
        List<Object> a = new ArrayList<>(List.of(uid, inst()));
        StringBuilder w = new StringBuilder(" where n.institution_id = ?");
        if (kind != null && !kind.isBlank()) { w.append(" and n.kind = ?"); a.add(up(kind)); }
        if (unreadOnly) w.append(" and r.notification_id is null");
        String from = " from school_notifications n left join school_notification_reads r on r.notification_id = n.id and r.user_id = ?";
        long total = count("select count(*)" + from + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        List<Notif> items = jdbc.query("select n.id, n.kind, n.title, n.body, n.link, n.created_at, r.notification_id is not null" + from + w + " order by n.created_at desc, n.id desc limit ? offset ?",
                (rs, n) -> new Notif(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), iso(rs.getTimestamp(6)), rs.getBoolean(7)), a.toArray());
        return new NotifPage(items, total, pg, sz, unread());
    }

    public void markRead(Long id) {
        jdbc.update("insert into school_notification_reads (notification_id, user_id) select n.id, ? from school_notifications n where n.id = ? and n.institution_id = ? on conflict do nothing", current.principal().userId(), id, inst());
    }

    public void markAllRead() {
        jdbc.update("insert into school_notification_reads (notification_id, user_id) select n.id, ? from school_notifications n where n.institution_id = ? on conflict do nothing", current.principal().userId(), inst());
    }

    // ------------------------------------------------------------------ announcements

    private static final String ANN_STATUS = "case when status = 'PUBLISHED' and publish_date > (now() at time zone 'Asia/Kolkata')::date then 'SCHEDULED' else status end";

    @Transactional(readOnly = true)
    public AnnPage announcements(String audience, String status, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where institution_id = ?");
        if (audience != null && !audience.isBlank()) { w.append(" and audience = ?"); a.add(up(audience)); }
        if (status != null && !status.isBlank()) { w.append(" and ").append(ANN_STATUS).append(" = ?"); a.add(up(status)); }
        long total = count("select count(*) from school_announcements" + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        return new AnnPage(jdbc.query("select id, title, body, audience, publish_date, " + ANN_STATUS + ", created_by from school_announcements" + w + " order by publish_date desc, id desc limit ? offset ?",
                (rs, n) -> new AnnRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDate(5).toLocalDate().toString(), rs.getString(6), rs.getString(7)), a.toArray()), total, pg, sz);
    }

    private Object[] checkAnn(AnnBody b) {
        Map<String, String> e = new LinkedHashMap<>();
        String title = clip(b.title(), 200);
        if (title == null) e.put("title", "Required.");
        String body = Html.clean(b.body());
        if (Html.text(body).isEmpty()) e.put("body", "Write the announcement.");
        String aud = up(b.audience());
        if (!has(ANN_AUDIENCES, aud)) e.put("audience", "Choose an audience.");
        LocalDate d = null;
        try { d = LocalDate.parse(b.publishDate() == null ? "" : b.publishDate().trim()); } catch (DateTimeParseException x) { e.put("publishDate", "Choose a publish date."); }
        if (d != null && d.isAfter(LocalDate.now(IST).plusYears(1))) e.put("publishDate", "Choose a date within the next year.");
        String st = b.status() == null || b.status().isBlank() ? "DRAFT" : up(b.status());
        if (!st.equals("DRAFT") && !st.equals("PUBLISHED")) e.put("status", "Choose Draft or Published.");
        fail(e);
        return new Object[] { title, body, aud, Date.valueOf(d), st };
    }

    public Map<String, Object> addAnnouncement(AnnBody b) {
        Object[] t = checkAnn(b);
        Long id = jdbc.queryForObject("insert into school_announcements (institution_id, title, body, audience, publish_date, status, created_by) values (?,?,?,?,?,?,?) returning id", Long.class, inst(), t[0], t[1], t[2], t[3], t[4], actor());
        audit.record("ANNOUNCEMENT_CREATED", "ANNOUNCEMENT", id, inst(), "Announcement \"" + t[0] + "\" created as " + ((String) t[4]).toLowerCase());
        return Map.of("id", id);
    }

    public Map<String, Object> updateAnnouncement(Long id, AnnBody b) {
        if (count("select count(*) from school_announcements where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("Announcement");
        Object[] t = checkAnn(b);
        jdbc.update("update school_announcements set title = ?, body = ?, audience = ?, publish_date = ?, status = ?, updated_at = now() where id = ?", t[0], t[1], t[2], t[3], t[4], id);
        audit.record("ANNOUNCEMENT_UPDATED", "ANNOUNCEMENT", id, inst(), "Announcement \"" + t[0] + "\" updated");
        return Map.of("id", id);
    }

    public Map<String, Object> announcementStatus(Long id, String status) {
        String st = up(status);
        if (!st.equals("DRAFT") && !st.equals("PUBLISHED")) throw ApiException.badRequest("Choose Draft or Published.");
        String title = jdbc.query("select title from school_announcements where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Announcement"));
        jdbc.update("update school_announcements set status = ?, updated_at = now() where id = ?", st, id);
        audit.record(st.equals("PUBLISHED") ? "ANNOUNCEMENT_PUBLISHED" : "ANNOUNCEMENT_UNPUBLISHED", "ANNOUNCEMENT", id, inst(), "Announcement \"" + title + "\" " + (st.equals("PUBLISHED") ? "published" : "moved back to draft"));
        return Map.of("id", id, "status", st);
    }

    public void deleteAnnouncement(Long id) {
        String title = jdbc.query("select title from school_announcements where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Announcement"));
        jdbc.update("delete from school_announcements where id = ?", id);
        audit.record("ANNOUNCEMENT_DELETED", "ANNOUNCEMENT", id, inst(), "Announcement \"" + title + "\" deleted");
    }
}
