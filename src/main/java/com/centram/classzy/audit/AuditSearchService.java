package com.centram.classzy.audit;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.common.ApiException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only search over the audit trail. Event type, module and status are worked out from the stored
 * action and entity type, so every older row is covered without rewriting any data.
 * Audit rows are never edited or deleted here.
 */
@Service
@Transactional(readOnly = true)
public class AuditSearchService {

    static final String EVENT_SQL = "case when a.action = 'LOGIN_FAILED' then 'FAILED_LOGIN' when a.action = 'LOGIN' then 'LOGIN' when a.action = 'LOGOUT' then 'LOGOUT'"
            + " when a.action like 'BACKUP%' then 'BACKUP' when a.action like '%DELETED' or a.action like '%REMOVED' then 'DELETE'"
            + " when a.action like '%CREATED' or a.action like '%ADDED' or a.action in ('REPORT_GENERATED','REPORT_SCHEDULED','PAYMENT_RECEIVED') then 'CREATE' else 'UPDATE' end";
    static final String MODULE_SQL = "case when a.action in ('LOGIN','LOGIN_FAILED','LOGOUT') then 'Authentication'"
            + " when upper(a.entity_type) = 'USER' then 'User Management' when upper(a.entity_type) like 'INSTITUTION%' then 'Institution'"
            + " when upper(a.entity_type) in ('TICKET','KB_ARTICLE') then 'Support & Tickets' when upper(a.entity_type) like 'MODULE%' then 'Modules & Features'"
            + " when upper(a.entity_type) in ('INVOICE','PAYMENT','PAYMENT_METHOD') then 'Subscription & Billing' when upper(a.entity_type) in ('REPORT','REPORT_SCHEDULE') then 'Reports & Analytics'"
            + " when upper(a.entity_type) in ('BACKUP','INSTANCE','INTEGRATION','SYSTEM_SETTINGS') then 'System' when upper(a.entity_type) = 'PLATFORM_SETTINGS' then 'Settings'"
            + " when upper(a.entity_type) = 'COMMUNICATION' then 'Communications' when upper(a.entity_type) like 'TRANSPORT%' then 'Transport'"
            + " when upper(a.entity_type) like 'TIMETABLE%' or upper(a.entity_type) like 'RULE%' then 'Timetable' when upper(a.entity_type) in ('RESULTTEMPLATE','REPORTTEMPLATE') then 'Exams & Grading'"
            + " when upper(a.entity_type) in ('SMSSENDERID','PUSHCATEGORY') then 'Communication Settings' else 'General' end";
    static final String STATUS_SQL = "case when a.action like '%FAIL%' then 'FAILED' else 'SUCCESS' end";

    static final List<String[]> EVENTS = List.of(new String[] { "LOGIN", "Login" }, new String[] { "FAILED_LOGIN", "Failed Login" }, new String[] { "CREATE", "Create" },
            new String[] { "UPDATE", "Update" }, new String[] { "DELETE", "Delete" }, new String[] { "BACKUP", "Backup" });
    static final List<String> MODULES = List.of("Authentication", "User Management", "Institution", "Subscription & Billing", "Modules & Features", "Communications", "Communication Settings",
            "Reports & Analytics", "Support & Tickets", "System", "Settings", "Transport", "Timetable", "Exams & Grading", "General");
    static final int EXPORT_CAP = 10000;

    public record Card(long total, Integer changePct) {}
    public record Summary(Card total, Card userActivities, Card systemChanges, Card securityEvents, Card loginActivities) {}
    public record Row(long id, String eventId, String at, String actorEmail, String actorName, String actorRole, String event, String action, String module, String details, String ip, String status) {}
    public record Page(List<Row> items, long total, int page, int size) {}
    public record Detail(Row row, String device, String location, String oldValue, String newValue, Map<String, String> extra) {}
    public record Option(String value, String label) {}
    public record Options(List<Option> events, List<String> modules, List<Option> users, List<Option> statuses, List<Option> ranges) {}

    private final JdbcTemplate jdbc;

    public AuditSearchService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }

    // ------------------------------------------------------------------ summary

    public Summary summary() {
        String login = "a.action in ('LOGIN','LOGIN_FAILED','LOGOUT')";
        String[] preds = {
            "true",
            "a.actor_email is not null and a.action not in ('LOGIN','LOGIN_FAILED','LOGOUT')",
            "(" + MODULE_SQL + ") in ('System','Settings','Modules & Features')",
            "a.action in ('LOGIN_FAILED','USER_PASSWORD_RESET','USER_STATUS_CHANGED','MODULE_ACCESS_CHANGED')",
            login };
        StringBuilder sql = new StringBuilder("select ");
        for (int i = 0; i < preds.length; i++) {
            if (i > 0) sql.append(", ");
            String p = preds[i];
            sql.append("count(*) filter (where ").append(p).append("), ")
               .append("count(*) filter (where ").append(p).append(" and a.created_at >= now() - interval '30 days'), ")
               .append("count(*) filter (where ").append(p).append(" and a.created_at >= now() - interval '60 days' and a.created_at < now() - interval '30 days')");
        }
        sql.append(" from audit_logs a");
        long[] v = jdbc.queryForObject(sql.toString(), (rs, n) -> {
            long[] o = new long[15];
            for (int i = 0; i < 15; i++) o[i] = rs.getLong(i + 1);
            return o;
        });
        Card[] c = new Card[5];
        for (int i = 0; i < 5; i++) {
            long cur = v[i * 3 + 1], prev = v[i * 3 + 2];
            c[i] = new Card(v[i * 3], prev == 0 ? null : (int) Math.round((cur - prev) * 100.0 / prev));
        }
        return new Summary(c[0], c[1], c[2], c[3], c[4]);
    }

    // ------------------------------------------------------------------ list

    private record Where(String sql, List<Object> args) {}

    private Where where(String q, String range, String from, String to, String event, String module, String user, String status) {
        List<String> w = new ArrayList<>();
        List<Object> a = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.add("(coalesce(a.actor_email,'') ilike ? or coalesce(u.full_name,'') ilike ? or a.action ilike ? or a.description ilike ? or coalesce(a.ip_address,'') ilike ? or (" + MODULE_SQL + ") ilike ?)");
            for (int i = 0; i < 6; i++) a.add(like);
        }
        String r = range == null ? "30D" : range.toUpperCase();
        switch (r) {
            case "TODAY" -> { w.add("a.created_at >= ?"); a.add(Timestamp.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant())); }
            case "7D" -> w.add("a.created_at >= now() - interval '7 days'");
            case "30D" -> w.add("a.created_at >= now() - interval '30 days'");
            case "90D" -> w.add("a.created_at >= now() - interval '90 days'");
            case "ALL" -> { }
            case "CUSTOM" -> {
                try {
                    if (from != null && !from.isBlank()) { w.add("a.created_at >= ?"); a.add(Timestamp.from(LocalDate.parse(from).atStartOfDay(ZoneId.systemDefault()).toInstant())); }
                    if (to != null && !to.isBlank()) { w.add("a.created_at < ?"); a.add(Timestamp.from(LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())); }
                } catch (DateTimeParseException e) { throw ApiException.badRequest("Dates must look like 2026-10-12."); }
            }
            default -> throw ApiException.badRequest("Unknown date range.");
        }
        if (event != null && !event.isBlank()) {
            if (EVENTS.stream().noneMatch(e -> e[0].equals(event))) throw ApiException.badRequest("Unknown event type.");
            w.add("(" + EVENT_SQL + ") = ?"); a.add(event);
        }
        if (module != null && !module.isBlank()) {
            if (!MODULES.contains(module)) throw ApiException.badRequest("Unknown module.");
            w.add("(" + MODULE_SQL + ") = ?"); a.add(module);
        }
        if (user != null && !user.isBlank()) {
            if (user.equals("__SYSTEM__")) w.add("a.actor_email is null");
            else { w.add("lower(a.actor_email) = lower(?)"); a.add(user.trim()); }
        }
        if (status != null && !status.isBlank()) {
            if (!status.equals("SUCCESS") && !status.equals("FAILED")) throw ApiException.badRequest("Unknown status.");
            w.add("(" + STATUS_SQL + ") = ?"); a.add(status);
        }
        return new Where(w.isEmpty() ? "" : " where " + String.join(" and ", w), a);
    }

    private static final String FROM = " from audit_logs a left join users u on lower(u.email) = lower(a.actor_email)";
    private static final String COLS = "a.id, a.created_at, a.actor_email, u.full_name, a.actor_role, " + "(" + EVENT_SQL + "), a.action, (" + MODULE_SQL + "), a.description, a.ip_address, (" + STATUS_SQL + ")";

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        long id = rs.getLong(1);
        Timestamp t = rs.getTimestamp(2);
        String ev = String.format("LOG-%d-%06d", t.toInstant().atZone(ZoneId.systemDefault()).getYear(), id);
        return new Row(id, ev, iso(t), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11));
    }

    public Page list(String q, String range, String from, String to, String event, String module, String user, String status, String sort, int page, int size) {
        Where w = where(q, range, from, to, event, module, user, status);
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 0);
        Long total = jdbc.queryForObject("select count(*)" + FROM + w.sql(), Long.class, w.args().toArray());
        List<Object> args = new ArrayList<>(w.args());
        args.add(sz);
        args.add((long) pg * sz);
        String dir = "asc".equalsIgnoreCase(sort) ? "asc" : "desc";
        List<Row> items = jdbc.query("select " + COLS + FROM + w.sql() + " order by a.created_at " + dir + ", a.id " + dir + " limit ? offset ?", (rs, i) -> row(rs), args.toArray());
        return new Page(items, total == null ? 0 : total, pg, sz);
    }

    // ------------------------------------------------------------------ detail

    public Detail detail(long id) {
        List<Row> r = jdbc.query("select " + COLS + FROM + " where a.id = ?", (rs, i) -> row(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Log entry");
        Row row = r.get(0);
        Object[] x = jdbc.query("select a.user_agent, a.old_value, a.new_value, a.entity_type, a.entity_id, a.institution_id from audit_logs a where a.id = ?",
                (rs, i) -> new Object[] { rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getObject(6) == null ? null : rs.getLong(6) }, id).get(0);
        Map<String, String> extra = new LinkedHashMap<>();
        String type = (String) x[3];
        Long entityId = (Long) x[4], instId = (Long) x[5];
        if (type != null) extra.put("Entity", type + (entityId == null ? "" : " #" + entityId));
        if ("USER".equalsIgnoreCase(type) && entityId != null) {
            jdbc.query("select full_name, email from users where id = ?", rs -> { extra.put("Name", rs.getString(1)); extra.put("Email", rs.getString(2)); }, entityId);
        }
        if (instId != null) jdbc.query("select name from institutions where id = ?", rs -> { extra.put("Institution", rs.getString(1)); }, instId);
        if (row.actorRole() != null) extra.put("Actor role", row.actorRole());
        return new Detail(row, device((String) x[0]), null, (String) x[1], (String) x[2], extra);
    }

    static String device(String ua) {
        if (ua == null || ua.isBlank()) return null;
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android" : (ua.contains("iPhone") || ua.contains("iPad")) ? "iOS" : ua.contains("Mac OS X") ? "macOS" : ua.contains("Linux") ? "Linux" : null;
        String browser = null;
        java.util.regex.Matcher m;
        if ((m = java.util.regex.Pattern.compile("Edg(?:e|A|iOS)?/(\\d+(?:\\.\\d+)?)").matcher(ua)).find()) browser = "Edge " + m.group(1);
        else if ((m = java.util.regex.Pattern.compile("OPR/(\\d+(?:\\.\\d+)?)").matcher(ua)).find()) browser = "Opera " + m.group(1);
        else if ((m = java.util.regex.Pattern.compile("(?:Chrome|CriOS)/(\\d+(?:\\.\\d+)?)").matcher(ua)).find()) browser = "Chrome " + m.group(1);
        else if ((m = java.util.regex.Pattern.compile("(?:Firefox|FxiOS)/(\\d+(?:\\.\\d+)?)").matcher(ua)).find()) browser = "Firefox " + m.group(1);
        else if ((m = java.util.regex.Pattern.compile("Version/(\\d+(?:\\.\\d+)?).*Safari").matcher(ua)).find()) browser = "Safari " + m.group(1);
        else if (ua.toLowerCase().startsWith("curl")) browser = "curl";
        if (os == null && browser == null) return ua.length() > 60 ? ua.substring(0, 60) : ua;
        return (os == null ? "Unknown OS" : os) + " / " + (browser == null ? "Unknown browser" : browser);
    }

    // ------------------------------------------------------------------ options & export

    public Options options() {
        List<Option> users = new ArrayList<>();
        users.add(new Option("__SYSTEM__", "System (automatic)"));
        jdbc.query("select a.actor_email, max(u.full_name) from audit_logs a left join users u on lower(u.email) = lower(a.actor_email) where a.actor_email is not null group by a.actor_email order by 2 nulls last, 1 limit 300",
                rs -> { String e = rs.getString(1), n = rs.getString(2); users.add(new Option(e, n == null || n.isBlank() ? e : n + " (" + e + ")")); });
        return new Options(EVENTS.stream().map(e -> new Option(e[0], e[1])).toList(), MODULES, users,
                List.of(new Option("SUCCESS", "Success"), new Option("FAILED", "Failed")),
                List.of(new Option("TODAY", "Today"), new Option("7D", "Last 7 Days"), new Option("30D", "Last 30 Days"), new Option("90D", "Last 90 Days"), new Option("ALL", "All time"), new Option("CUSTOM", "Custom range")));
    }

    private static String cell(String v) {
        if (v == null) return "";
        String s = v.replace("\r", " ").replace("\n", " ");
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public byte[] export(String q, String range, String from, String to, String event, String module, String user, String status, String sort) {
        Where w = where(q, range, from, to, event, module, user, status);
        List<Object> args = new ArrayList<>(w.args());
        args.add(EXPORT_CAP);
        String dir = "asc".equalsIgnoreCase(sort) ? "asc" : "desc";
        List<Row> rows = jdbc.query("select " + COLS + FROM + w.sql() + " order by a.created_at " + dir + ", a.id " + dir + " limit ?", (rs, i) -> row(rs), args.toArray());
        StringBuilder sb = new StringBuilder("﻿Event ID,Date & Time,User,Email,Role,Event,Action,Module,Details,IP Address,Status\r\n");
        for (Row r : rows) {
            sb.append(String.join(",", cell(r.eventId()), cell(r.at()), cell(r.actorName() != null ? r.actorName() : r.actorEmail() == null ? "System" : r.actorEmail()), cell(r.actorEmail()), cell(r.actorRole()),
                    cell(r.event()), cell(r.action()), cell(r.module()), cell(r.details()), cell(r.ip()), cell(r.status()))).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
