package com.centram.classzy.comms;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.comms.CommsDto.Body;
import com.centram.classzy.comms.CommsDto.Option;
import com.centram.classzy.comms.CommsDto.Options;
import com.centram.classzy.comms.CommsDto.Page;
import com.centram.classzy.comms.CommsDto.Resolved;
import com.centram.classzy.comms.CommsDto.Row;
import com.centram.classzy.comms.CommsDto.Summary;
import com.centram.classzy.security.AppPrincipal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Super Admin communications. Sending records the message and counts who it reaches; it does not hand the message to an
 * email/SMS/push provider yet. Scheduled messages are promoted to Sent the next time the list is read after their time.
 */
@Service
@Transactional
public class CommsService {

    static final Set<String> TYPES = Set.of("ANNOUNCEMENT", "EMAIL", "IN_APP", "SMS", "PUSH");
    static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    static final Set<String> KINDS = Set.of("ALL_USERS", "USER_GROUPS", "INSTITUTIONS", "CLASSES");
    static final Map<String, String> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("STUDENT", "Students");
        GROUPS.put("TEACHER", "Teachers");
        GROUPS.put("PARENT", "Parents");
        GROUPS.put("STAFF", "Staff");
        GROUPS.put("ADMINISTRATOR", "Administrators");
    }

    private static final String COLS = "id, title, description, comm_type, priority, audience_kind, audience_targets, subject, body, status, scheduled_at, sent_at, recipients, created_at";
    private static final String WHEN = "coalesce(sent_at, scheduled_at, created_at)";

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CommsService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }

    private static List<String> split(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private Row row(ResultSet rs) throws SQLException {
        String kind = rs.getString(6);
        List<String> targets = split(rs.getString(7));
        int rec = rs.getInt(13);
        return new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), kind, targets, label(kind, targets),
                rs.getString(8), rs.getString(9), rs.getString(10), iso(rs.getTimestamp(11)), iso(rs.getTimestamp(12)), rs.wasNull() ? null : rec, iso(rs.getTimestamp(14)));
    }

    String label(String kind, List<String> targets) {
        return switch (kind) {
            case "USER_GROUPS" -> targets.stream().map(t -> GROUPS.getOrDefault(t, t)).collect(Collectors.joining(", "));
            case "INSTITUTIONS" -> targets.size() == 1 ? jdbc.queryForList("select name from institutions where id = ?", String.class, Long.valueOf(targets.get(0))).stream().findFirst().orElse("1 institution") : targets.size() + " institutions";
            case "CLASSES" -> targets.size() + (targets.size() == 1 ? " section" : " sections");
            default -> "All Users";
        };
    }

    /** Turns scheduled messages whose time has passed into Sent. */
    void promoteDue() {
        List<Long> due = jdbc.queryForList("select id from communications where status = 'SCHEDULED' and scheduled_at <= now()", Long.class);
        for (Long id : due) {
            Row r = get(id);
            jdbc.update("update communications set status = 'SENT', sent_at = now(), recipients = ?, updated_at = now() where id = ?", count(r.audienceKind(), r.targets()), id);
        }
    }

    @Transactional
    public Page list(String type, String status, String audience, String q, String from, String to, int page, int size) {
        promoteDue();
        StringBuilder w = new StringBuilder(" where 1 = 1");
        List<Object> p = new ArrayList<>();
        if (type != null && !type.isBlank()) { w.append(" and comm_type = ?"); p.add(type); }
        if (status != null && !status.isBlank()) { w.append(" and status = ?"); p.add(status); }
        if (audience != null && !audience.isBlank()) {
            if (KINDS.contains(audience)) { w.append(" and audience_kind = ?"); p.add(audience); }
            else { w.append(" and audience_kind = 'USER_GROUPS' and (',' || audience_targets || ',') like ?"); p.add("%," + audience + ",%"); }
        }
        if (q != null && !q.isBlank()) { w.append(" and (lower(title) like ? or lower(coalesce(description,'')) like ? or lower(coalesce(body,'')) like ?)"); String k = "%" + q.trim().toLowerCase() + "%"; p.add(k); p.add(k); p.add(k); }
        ZoneId z = ZoneId.of("Asia/Kolkata");
        if (from != null && !from.isBlank()) { w.append(" and ").append(WHEN).append(" >= ?"); p.add(Timestamp.from(parseDate(from).atStartOfDay(z).toInstant())); }
        if (to != null && !to.isBlank()) { w.append(" and ").append(WHEN).append(" < ?"); p.add(Timestamp.from(parseDate(to).plusDays(1).atStartOfDay(z).toInstant())); }
        long total = jdbc.queryForObject("select count(*) from communications" + w, Long.class, p.toArray());
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 1);
        List<Object> p2 = new ArrayList<>(p);
        p2.add(sz); p2.add((pg - 1) * sz);
        List<Row> rows = jdbc.query("select " + COLS + " from communications" + w + " order by " + WHEN + " desc, id desc limit ? offset ?", (rs, i) -> row(rs), p2.toArray());
        return new Page(rows, total, pg, sz);
    }

    private static LocalDate parseDate(String s) {
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { throw ApiException.badRequest("Invalid date: " + s); }
    }

    @Transactional
    public Summary summary() {
        promoteDue();
        long total = jdbc.queryForObject("select count(*) from communications", Long.class);
        long month = jdbc.queryForObject("select count(*) from communications where created_at >= date_trunc('month', now())", Long.class);
        long sent = jdbc.queryForObject("select count(*) from communications where status = 'SENT'", Long.class);
        long drafts = jdbc.queryForObject("select count(*) from communications where status = 'DRAFT'", Long.class);
        long sched = jdbc.queryForObject("select count(*) from communications where status = 'SCHEDULED'", Long.class);
        long reached = jdbc.queryForObject("select coalesce(sum(recipients), 0) from communications where status = 'SENT'", Long.class);
        return new Summary(total, month, sent, drafts, sched, reached);
    }

    @Transactional(readOnly = true)
    public Options options() {
        List<Option> groups = GROUPS.entrySet().stream().map(e -> new Option(e.getKey(), e.getValue())).toList();
        List<Option> inst = jdbc.query("select id, name from institutions where lifecycle <> 'ARCHIVED' order by lower(name)", (rs, i) -> new Option(String.valueOf(rs.getLong(1)), rs.getString(2)));
        List<Option> secs = jdbc.query("select s.id, c.name || ' - ' || s.name || ' (' || i.name || ')' from sections s join school_classes c on c.id = s.school_class_id join institutions i on i.id = s.institution_id "
                + "where s.active and i.lifecycle <> 'ARCHIVED' order by lower(i.name), c.grade_level nulls first, c.name, s.name", (rs, i) -> new Option(String.valueOf(rs.getLong(1)), rs.getString(2)));
        return new Options(groups, inst, secs);
    }

    @Transactional(readOnly = true)
    public Resolved resolve(String kind, List<String> targets) {
        String k = kind == null ? "ALL_USERS" : kind;
        if (!KINDS.contains(k)) throw ApiException.badRequest("Unknown audience.");
        List<String> t = targets == null ? List.of() : targets;
        return new Resolved(count(k, t), label(k, t.isEmpty() && !k.equals("ALL_USERS") ? List.of() : t));
    }

    /** How many people a message to this audience would reach. */
    long count(String kind, List<String> targets) {
        if (kind.equals("ALL_USERS")) return jdbc.queryForObject("select count(*) from users where active", Long.class);
        if (targets.isEmpty()) return 0;
        String in = String.join(",", targets.stream().map(x -> "?").toList());
        Object[] args = switch (kind) {
            case "USER_GROUPS" -> targets.toArray();
            default -> targets.stream().map(Long::valueOf).toArray();
        };
        return switch (kind) {
            case "USER_GROUPS" -> jdbc.queryForObject("select count(*) from users where active and user_type in (" + in + ")", Long.class, args);
            case "INSTITUTIONS" -> jdbc.queryForObject("select count(*) from users where active and institution_id in (" + in + ")", Long.class, args);
            default -> jdbc.queryForObject("select count(*) from students where active and section_id in (" + in + ")", Long.class, args);
        };
    }

    @Transactional(readOnly = true)
    public Row get(Long id) {
        List<Row> r = jdbc.query("select " + COLS + " from communications where id = ?", (rs, i) -> row(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Communication");
        return r.get(0);
    }

    public Row create(Body b) {
        Clean c = validate(b);
        Long id = jdbc.queryForObject("insert into communications (title, description, comm_type, priority, audience_kind, audience_targets, subject, body, status, scheduled_at, sent_at, recipients, created_by) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class, c.args(actor()));
        audit.record("COMMUNICATION_" + c.status, "COMMUNICATION", id, null, c.status.equals("SENT") ? "Sent \"" + c.title + "\"" : (c.status.equals("SCHEDULED") ? "Scheduled \"" : "Saved draft \"") + c.title + "\"");
        return get(id);
    }

    public Row update(Long id, Body b) {
        Row cur = get(id);
        if (cur.status().equals("SENT")) throw ApiException.conflict("A sent communication can't be edited. Duplicate it instead.");
        Clean c = validate(b);
        jdbc.update("update communications set title = ?, description = ?, comm_type = ?, priority = ?, audience_kind = ?, audience_targets = ?, subject = ?, body = ?, status = ?, scheduled_at = ?, sent_at = ?, recipients = ?, updated_at = now() where id = ?",
                c.updateArgs(id));
        audit.record("COMMUNICATION_" + c.status, "COMMUNICATION", id, null, (c.status.equals("SENT") ? "Sent \"" : "Updated \"") + c.title + "\"");
        return get(id);
    }

    public Row send(Long id) {
        Row r = get(id);
        if (r.status().equals("SENT")) throw ApiException.conflict("Already sent.");
        Map<String, String> err = contentErrors(r.type(), r.subject(), r.body());
        if (r.targets().isEmpty() && !r.audienceKind().equals("ALL_USERS")) err.put("audience", "Choose who should receive this.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        jdbc.update("update communications set status = 'SENT', sent_at = now(), scheduled_at = null, recipients = ?, updated_at = now() where id = ?", count(r.audienceKind(), r.targets()), id);
        audit.record("COMMUNICATION_SENT", "COMMUNICATION", id, null, "Sent \"" + r.title() + "\"");
        return get(id);
    }

    public Row duplicate(Long id) {
        Row r = get(id);
        Long nid = jdbc.queryForObject("insert into communications (title, description, comm_type, priority, audience_kind, audience_targets, subject, body, status, created_by) values (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?) returning id",
                Long.class, ("Copy of " + r.title()).substring(0, Math.min(150, ("Copy of " + r.title()).length())), r.description(), r.type(), r.priority(), r.audienceKind(), String.join(",", r.targets()), r.subject(), r.body(), actor());
        audit.record("COMMUNICATION_DRAFT", "COMMUNICATION", nid, null, "Duplicated \"" + r.title() + "\"");
        return get(nid);
    }

    public void delete(Long id) {
        Row r = get(id);
        jdbc.update("delete from communications where id = ?", id);
        audit.record("COMMUNICATION_DELETED", "COMMUNICATION", id, null, "Deleted \"" + r.title() + "\"");
    }

    private static String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppPrincipal p ? p.email() : null;
    }

    private record Clean(String title, String description, String type, String priority, String kind, String targets, String subject, String body, String status, Timestamp scheduled, Timestamp sentAt, Integer recipients) {
        Object[] args(String actor) { return new Object[] { title, description, type, priority, kind, targets, subject, body, status, scheduled, sentAt, recipients, actor }; }
        Object[] updateArgs(Long id) { return new Object[] { title, description, type, priority, kind, targets, subject, body, status, scheduled, sentAt, recipients, id }; }
    }

    private Map<String, String> contentErrors(String type, String subject, String body) {
        Map<String, String> err = new LinkedHashMap<>();
        int max = type.equals("SMS") ? 480 : type.equals("PUSH") ? 240 : 5000;
        if (body == null || body.isBlank()) err.put("body", "Write the message content.");
        else if (body.length() > max) err.put("body", "Use " + max + " characters or fewer for this type.");
        if (type.equals("EMAIL") && (subject == null || subject.isBlank())) err.put("subject", "An email needs a subject.");
        return err;
    }

    private Clean validate(Body b) {
        Map<String, String> err = new LinkedHashMap<>();
        String title = b.title() == null ? "" : b.title().trim();
        String desc = b.description() == null || b.description().isBlank() ? null : b.description().trim();
        String type = b.type() == null ? "" : b.type().trim().toUpperCase();
        String pr = b.priority() == null || b.priority().isBlank() ? "NORMAL" : b.priority().trim().toUpperCase();
        String kind = b.audienceKind() == null || b.audienceKind().isBlank() ? "ALL_USERS" : b.audienceKind().trim().toUpperCase();
        String action = b.action() == null ? "DRAFT" : b.action().trim().toUpperCase();
        String subject = b.subject() == null || b.subject().isBlank() ? null : b.subject().trim();
        String body = b.body() == null || b.body().isBlank() ? null : b.body().trim();

        if (title.isEmpty()) err.put("title", "Required."); else if (title.length() > 150) err.put("title", "Use 150 characters or fewer.");
        if (desc != null && desc.length() > 500) err.put("description", "Use 500 characters or fewer.");
        if (!TYPES.contains(type)) err.put("type", "Choose a communication type.");
        if (!PRIORITIES.contains(pr)) err.put("priority", "Choose a priority.");
        if (!KINDS.contains(kind)) err.put("audienceKind", "Choose an audience.");
        if (subject != null && subject.length() > 200) err.put("subject", "Use 200 characters or fewer.");
        if (!Set.of("DRAFT", "SEND", "SCHEDULE").contains(action)) err.put("action", "Unknown action.");

        List<String> targets = new ArrayList<>();
        if (b.targets() != null) b.targets().stream().filter(x -> x != null && !x.isBlank()).map(String::trim).distinct().forEach(targets::add);
        if (!err.containsKey("audienceKind")) {
            if (kind.equals("ALL_USERS")) targets.clear();
            else if (targets.isEmpty() && !action.equals("DRAFT")) err.put("audience", "Choose who should receive this.");
            else if (kind.equals("USER_GROUPS") && !GROUPS.keySet().containsAll(targets)) err.put("audience", "Unknown user group.");
            else if (!kind.equals("USER_GROUPS")) {
                try { targets.forEach(Long::valueOf); } catch (NumberFormatException e) { err.put("audience", "Invalid selection."); }
            }
        }
        Timestamp when = null;
        if (action.equals("SCHEDULE")) {
            try { when = b.scheduledAt() == null ? null : Timestamp.from(Instant.parse(b.scheduledAt())); } catch (DateTimeParseException e) { err.put("scheduledAt", "Invalid date and time."); }
            if (when == null && !err.containsKey("scheduledAt")) err.put("scheduledAt", "Pick a date and time.");
            else if (when != null && when.toInstant().isBefore(Instant.now().plusSeconds(30))) err.put("scheduledAt", "Choose a time in the future.");
        }
        if (!action.equals("DRAFT") && TYPES.contains(type)) err.putAll(contentErrors(type, subject, body));
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        String status = action.equals("SEND") ? "SENT" : action.equals("SCHEDULE") ? "SCHEDULED" : "DRAFT";
        Integer recipients = status.equals("SENT") ? (int) count(kind, targets) : null;
        return new Clean(title, desc, type, pr, kind, String.join(",", targets), subject, body, status, status.equals("SCHEDULED") ? when : null, status.equals("SENT") ? Timestamp.from(Instant.now()) : null, recipients);
    }
}
