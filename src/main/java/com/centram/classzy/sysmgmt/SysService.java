package com.centram.classzy.sysmgmt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.communication.CommSpec;
import com.centram.classzy.communication.CommSpec.Spec;
import com.centram.classzy.communication.CommSpec.Type;
import com.centram.classzy.security.AppPrincipal;
import com.centram.classzy.sysmgmt.SysDto.BackupBody;
import com.centram.classzy.sysmgmt.SysDto.BackupRow;
import com.centram.classzy.sysmgmt.SysDto.Card;
import com.centram.classzy.sysmgmt.SysDto.DataRow;
import com.centram.classzy.sysmgmt.SysDto.Header;
import com.centram.classzy.sysmgmt.SysDto.InstanceBody;
import com.centram.classzy.sysmgmt.SysDto.InstanceRow;
import com.centram.classzy.sysmgmt.SysDto.IntegrationBody;
import com.centram.classzy.sysmgmt.SysDto.IntegrationRow;
import com.centram.classzy.sysmgmt.SysDto.Overview;
import com.centram.classzy.sysmgmt.SysDto.SecurityRow;
import com.centram.classzy.sysmgmt.SysDto.ServiceRow;
import com.centram.classzy.system.HealthService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * System Management. Real: service checks (from the health monitor), database size, security checks, data exports and ZIP-of-CSV backups.
 * Records only: instances/environments and integrations (nothing is provisioned or connected), and the security/system settings (stored, not enforced).
 */
@Service
@Transactional
public class SysService {

    private static final Map<String, Spec> SECURITY = new LinkedHashMap<>();
    private static final Map<String, Spec> CONFIG = new LinkedHashMap<>();
    private static void sec(String k, Type t, String def, int min, int max) { SECURITY.put(k, new Spec(k, t, def, false, min, max, List.of())); }
    private static void cfg(String k, Type t, String def, int min, int max) { CONFIG.put(k, new Spec(k, t, def, false, min, max, List.of())); }
    static {
        sec("passwordMinLength", Type.INT, "8", 6, 32);
        sec("mfaRequired", Type.BOOL, "false", 0, 0);
        sec("ssoEnabled", Type.BOOL, "false", 0, 0);
        sec("ipAllowlist", Type.TEXT, "", 0, 0);
        sec("sessionTimeoutMinutes", Type.INT, "480", 5, 1440);
        cfg("storageCapacityGb", Type.INT, "100", 1, 100000);
        cfg("maintenanceMode", Type.BOOL, "false", 0, 0);
        cfg("maintenanceMessage", Type.TEXT, "", 0, 0);
        cfg("logRetentionDays", Type.INT, "90", 7, 3650);
        cfg("backupRetentionDays", Type.INT, "30", 1, 3650);
    }

    static final Set<String> KINDS = Set.of("PRODUCTION", "NON_PROD");
    static final Set<String> ENVS = Set.of("PROD", "QA", "PREVIEW", "DEV");
    static final Set<String> INST_STATUS = Set.of("ACTIVE", "REFRESHING", "STOPPED");
    static final Set<String> INTEG_TYPES = Set.of("SAP", "API", "DEVICE", "OTHER");
    static final Set<String> BACKUP_TYPES = Set.of("FULL", "DATABASE", "CONFIGURATION");
    private static final Pattern VERSION = Pattern.compile("^[A-Za-z0-9._+\\-]{1,20}$");
    private static final Pattern IP = Pattern.compile("^([0-9]{1,3}(\\.[0-9]{1,3}){3}(/[0-9]{1,2})?|[0-9a-fA-F:]{2,39}(/[0-9]{1,3})?)$");
    private static final Set<String> SKIP_TABLES = Set.of("flyway_schema_history", "system_backups", "comm_settings", "catalog_item_attrs", "ticket_attachments");
    private static final Pattern SECRETISH = Pattern.compile(".*(password|secret|token).*", Pattern.CASE_INSENSITIVE);
    private static final long MAX_BACKUP = 100L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final HealthService health;
    private final String jwtSecret;

    public SysService(JdbcTemplate jdbc, AuditService audit, HealthService health, @Value("${classzy.jwt.secret:}") String jwtSecret) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.health = health;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
    }

    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }

    private String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppPrincipal p ? p.email() : null;
    }

    // ------------------------------------------------------------------ settings

    private Map<String, String> stored() {
        Map<String, String> m = new LinkedHashMap<>();
        jdbc.query("select setting_key, setting_value from system_settings", rs -> { m.put(rs.getString(1), rs.getString(2)); });
        return m;
    }

    /** Shared with the Settings page so both pages read and write the same stored values. */
    @Transactional(readOnly = true)
    public Map<String, String> securityValues() { return values(SECURITY); }

    @Transactional(readOnly = true)
    public Map<String, String> configValues() { return values(CONFIG); }

    private Map<String, String> values(Map<String, Spec> specs) {
        Map<String, String> s = stored(), out = new LinkedHashMap<>();
        specs.forEach((k, sp) -> out.put(k, s.getOrDefault(k, sp.def())));
        return out;
    }

    private Map<String, Timestamp> stamps() {
        Map<String, Timestamp> m = new LinkedHashMap<>();
        jdbc.query("select setting_key, updated_at from system_settings", rs -> { m.put(rs.getString(1), rs.getTimestamp(2)); });
        return m;
    }

    public Map<String, String> saveSettings(boolean security, Map<String, String> raw) {
        Map<String, Spec> specs = security ? SECURITY : CONFIG;
        if (raw == null || raw.isEmpty()) throw ApiException.badRequest("Nothing to save.");
        Map<String, String> err = new LinkedHashMap<>(), clean = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            Spec sp = specs.get(e.getKey());
            if (sp == null) { err.put(e.getKey(), "Unknown setting."); continue; }
            try {
                String v = CommSpec.clean(sp, e.getValue());
                if (sp.key().equals("ipAllowlist") && v != null && !v.isBlank()) {
                    List<String> bad = java.util.Arrays.stream(v.split("[,\\s]+")).filter(x -> !x.isEmpty() && !IP.matcher(x).matches()).toList();
                    if (!bad.isEmpty()) throw new IllegalArgumentException("Enter IP addresses or ranges separated by commas (e.g. 203.0.113.5, 10.0.0.0/24). Invalid: " + bad.get(0));
                    v = java.util.Arrays.stream(v.split("[,\\s]+")).filter(x -> !x.isEmpty()).collect(Collectors.joining(", "));
                }
                if (sp.key().equals("maintenanceMessage") && v != null && v.length() > 300) throw new IllegalArgumentException("Use 300 characters or fewer.");
                clean.put(sp.key(), v == null ? "" : v);
            } catch (IllegalArgumentException ex) { err.put(sp.key(), ex.getMessage()); }
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        clean.forEach((k, v) -> jdbc.update("insert into system_settings (setting_key, setting_value, updated_by) values (?, ?, ?) on conflict (setting_key) do update set setting_value = excluded.setting_value, updated_at = now(), updated_by = excluded.updated_by", k, v, actor()));
        audit.record("SYSTEM_SETTINGS_UPDATED", "SYSTEM_SETTINGS", null, null, (security ? "Security" : "System configuration") + " settings updated: " + String.join(", ", clean.keySet()));
        return values(specs);
    }

    private List<SecurityRow> securityRows() {
        Map<String, String> v = values(SECURITY);
        Map<String, Timestamp> t = stamps();
        List<SecurityRow> rows = new ArrayList<>();
        rows.add(new SecurityRow("PASSWORD", "Password Policy", true, "Minimum " + v.get("passwordMinLength") + " characters", iso(t.get("passwordMinLength")), sub(v, "passwordMinLength")));
        boolean mfa = "true".equals(v.get("mfaRequired")), sso = "true".equals(v.get("ssoEnabled")), ip = !v.get("ipAllowlist").isBlank();
        rows.add(new SecurityRow("MFA", "Multi-Factor Authentication (MFA)", mfa, mfa ? "Required for all admins" : "Not required", iso(t.get("mfaRequired")), sub(v, "mfaRequired")));
        rows.add(new SecurityRow("SSO", "Single Sign-On (SSO)", sso, sso ? "Enabled" : "Disabled", iso(t.get("ssoEnabled")), sub(v, "ssoEnabled")));
        rows.add(new SecurityRow("IP", "IP Allowlist", ip, ip ? v.get("ipAllowlist") : "No restrictions", iso(t.get("ipAllowlist")), sub(v, "ipAllowlist")));
        rows.add(new SecurityRow("SESSION", "Session Timeout", true, v.get("sessionTimeoutMinutes") + " minutes", iso(t.get("sessionTimeoutMinutes")), sub(v, "sessionTimeoutMinutes")));
        return rows;
    }

    private static Map<String, String> sub(Map<String, String> v, String key) { return Map.of(key, v.get(key)); }

    // ------------------------------------------------------------------ overview

    @Transactional
    public Overview overview() {
        Map<String, String> cfg = values(CONFIG);
        List<BackupRow> backups = backups();
        List<String> issues = securityIssues(backups, values(SECURITY));
        HealthService.Snapshot snap = health.check();
        List<ServiceRow> monitoring = monitoring(snap);
        long healthy = monitoring.stream().filter(s -> s.status().equals("HEALTHY")).count();
        long counted = monitoring.stream().filter(s -> !s.status().equals("NOT_CONFIGURED")).count();
        String hs = snap.overall();
        Card healthCard = new Card(hs, hs.equals("OPERATIONAL") ? "Operational" : hs.equals("DEGRADED") ? "Degraded" : "Down", hs.equals("OPERATIONAL") ? "All services running" : healthy + " of " + counted + " services healthy");

        long used = jdbc.queryForObject("select pg_database_size(current_database())", Long.class);
        int cap = Integer.parseInt(cfg.get("storageCapacityGb"));
        double pct = Math.min(100.0, Math.round(used * 1000.0 / (cap * 1024.0 * 1024 * 1024)) / 10.0);
        long inst = jdbc.queryForObject("select count(*) from platform_instances", Long.class);
        long instNew = jdbc.queryForObject("select count(*) from platform_instances where created_at >= date_trunc('month', now())", Long.class);
        boolean critical = issues.stream().anyMatch(s -> s.startsWith("Critical"));
        Card sec = issues.isEmpty() ? new Card("OK", "Compliant", "No critical issues")
                : new Card(critical ? "RISK" : "WARN", critical ? "At Risk" : "Needs Attention", issues.size() + (issues.size() == 1 ? " issue found" : " issues found"));
        BackupRow last = backups.stream().filter(b -> b.status().equals("SUCCESS")).findFirst().orElse(null);
        Header h = new Header(healthCard, inst, instNew, used, cap, pct, sec, issues, last);
        return new Overview(h, instances(), integrations(), dataRows(), securityRows(), monitoring, backups, cfg);
    }

    private List<String> securityIssues(List<BackupRow> backups, Map<String, String> sec) {
        List<String> out = new ArrayList<>();
        if (jwtSecret.isBlank() || jwtSecret.contains("dev-only-secret") || jwtSecret.length() < 32) out.add("Critical: the sign-in token secret is the development default. Set the JWT_SECRET environment variable.");
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        boolean recent = backups.stream().anyMatch(b -> b.status().equals("SUCCESS") && Instant.parse(b.createdAt()).isAfter(weekAgo));
        if (!recent) out.add("No successful backup in the last 7 days.");
        if (Integer.parseInt(sec.get("passwordMinLength")) < 8) out.add("The minimum password length is below 8 characters.");
        return out;
    }

    // ------------------------------------------------------------------ instances

    private static final String INST = "select p.id, p.name, p.institution_id, i.name, p.kind, p.environment, p.version, p.status, p.last_refresh, p.notes from platform_instances p left join institutions i on i.id = p.institution_id";

    private InstanceRow instRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InstanceRow(rs.getLong(1), rs.getString(2), rs.getObject(3) == null ? null : rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), iso(rs.getTimestamp(9)), rs.getString(10));
    }

    private List<InstanceRow> instances() { return jdbc.query(INST + " order by p.created_at desc, p.id desc limit 200", (rs, n) -> instRow(rs)); }

    private InstanceRow instance(Long id) {
        List<InstanceRow> r = jdbc.query(INST + " where p.id = ?", (rs, n) -> instRow(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Instance");
        return r.get(0);
    }

    private Object[] instanceArgs(InstanceBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim();
        String kind = b.kind() == null ? "PRODUCTION" : b.kind().trim().toUpperCase();
        String env = b.environment() == null ? "PROD" : b.environment().trim().toUpperCase();
        String status = b.status() == null || b.status().isBlank() ? "ACTIVE" : b.status().trim().toUpperCase();
        String ver = b.version() == null || b.version().isBlank() ? null : b.version().trim();
        String notes = b.notes() == null || b.notes().isBlank() ? null : b.notes().trim();
        if (name.isEmpty()) err.put("name", "Required."); else if (name.length() > 150) err.put("name", "Use 150 characters or fewer.");
        if (!KINDS.contains(kind)) err.put("kind", "Choose Production or Non-Prod.");
        if (!ENVS.contains(env)) err.put("environment", "Choose an environment.");
        if (!INST_STATUS.contains(status)) err.put("status", "Choose a status.");
        if (ver != null && !VERSION.matcher(ver).matches()) err.put("version", "Use letters, digits and . _ - + (e.g. v2.5.1).");
        if (notes != null && notes.length() > 500) err.put("notes", "Use 500 characters or fewer.");
        if (b.institutionId() != null && jdbc.queryForObject("select count(*) from institutions where id = ?", Integer.class, b.institutionId()) == 0) err.put("institutionId", "Institution not found.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        return new Object[] { name, b.institutionId(), kind, env, ver, status, notes };
    }

    public InstanceRow createInstance(InstanceBody b) {
        Object[] a = instanceArgs(b);
        Long id = jdbc.queryForObject("insert into platform_instances (name, institution_id, kind, environment, version, status, notes, created_by, last_refresh) values (?, ?, ?, ?, ?, ?, ?, ?, now()) returning id", Long.class,
                a[0], a[1], a[2], a[3], a[4], a[5], a[6], actor());
        audit.record("INSTANCE_CREATED", "INSTANCE", id, (Long) a[1], "Created instance " + a[0]);
        return instance(id);
    }

    public InstanceRow updateInstance(Long id, InstanceBody b) {
        instance(id);
        Object[] a = instanceArgs(b);
        jdbc.update("update platform_instances set name = ?, institution_id = ?, kind = ?, environment = ?, version = ?, status = ?, notes = ? where id = ?", a[0], a[1], a[2], a[3], a[4], a[5], a[6], id);
        audit.record("INSTANCE_UPDATED", "INSTANCE", id, (Long) a[1], "Updated instance " + a[0]);
        return instance(id);
    }

    public InstanceRow refreshInstance(Long id) {
        InstanceRow i = instance(id);
        jdbc.update("update platform_instances set last_refresh = now(), status = 'ACTIVE' where id = ?", id);
        audit.record("INSTANCE_REFRESHED", "INSTANCE", id, i.institutionId(), "Recorded refresh of " + i.name());
        return instance(id);
    }

    public void deleteInstance(Long id) {
        InstanceRow i = instance(id);
        jdbc.update("delete from platform_instances where id = ?", id);
        audit.record("INSTANCE_DELETED", "INSTANCE", id, null, "Deleted instance " + i.name());
    }

    // ------------------------------------------------------------------ integrations

    private static final String INTEG = "select id, name, integration_type, source, target, endpoint, status, last_run, last_ms, last_message from platform_integrations";

    private IntegrationRow integRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IntegrationRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), iso(rs.getTimestamp(8)), rs.getObject(9) == null ? null : rs.getInt(9), rs.getString(10));
    }

    private List<IntegrationRow> integrations() { return jdbc.query(INTEG + " order by id desc limit 200", (rs, n) -> integRow(rs)); }

    private IntegrationRow integration(Long id) {
        List<IntegrationRow> r = jdbc.query(INTEG + " where id = ?", (rs, n) -> integRow(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Integration");
        return r.get(0);
    }

    private Object[] integArgs(IntegrationBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim();
        String type = b.type() == null ? "API" : b.type().trim().toUpperCase();
        String src = b.source() == null || b.source().isBlank() ? null : b.source().trim();
        String tgt = b.target() == null || b.target().isBlank() ? null : b.target().trim();
        String ep = b.endpoint() == null || b.endpoint().isBlank() ? null : b.endpoint().trim();
        if (name.isEmpty()) err.put("name", "Required."); else if (name.length() > 150) err.put("name", "Use 150 characters or fewer.");
        if (!INTEG_TYPES.contains(type)) err.put("type", "Choose a type.");
        if (src != null && src.length() > 60) err.put("source", "Use 60 characters or fewer.");
        if (tgt != null && tgt.length() > 60) err.put("target", "Use 60 characters or fewer.");
        if (ep != null) {
            try { URI u = URI.create(ep); if (u.getHost() == null || !(u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("https"))) throw new IllegalArgumentException(); if (ep.length() > 300) throw new IllegalArgumentException(); }
            catch (RuntimeException e) { err.put("endpoint", "Enter a full http(s) URL, e.g. https://api.example.com/health"); }
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        return new Object[] { name, type, src, tgt, ep };
    }

    public IntegrationRow createIntegration(IntegrationBody b) {
        Object[] a = integArgs(b);
        Long id = jdbc.queryForObject("insert into platform_integrations (name, integration_type, source, target, endpoint) values (?, ?, ?, ?, ?) returning id", Long.class, a);
        audit.record("INTEGRATION_CREATED", "INTEGRATION", id, null, "Added integration " + a[0]);
        return integration(id);
    }

    public IntegrationRow updateIntegration(Long id, IntegrationBody b) {
        integration(id);
        Object[] a = integArgs(b);
        jdbc.update("update platform_integrations set name = ?, integration_type = ?, source = ?, target = ?, endpoint = ? where id = ?", a[0], a[1], a[2], a[3], a[4], id);
        audit.record("INTEGRATION_UPDATED", "INTEGRATION", id, null, "Updated integration " + a[0]);
        return integration(id);
    }

    public IntegrationRow toggleIntegration(Long id) {
        IntegrationRow i = integration(id);
        boolean off = !i.status().equals("DISABLED");
        jdbc.update("update platform_integrations set status = ? where id = ?", off ? "DISABLED" : "ACTIVE", id);
        audit.record("INTEGRATION_UPDATED", "INTEGRATION", id, null, (off ? "Disabled " : "Enabled ") + i.name());
        return integration(id);
    }

    public void deleteIntegration(Long id) {
        IntegrationRow i = integration(id);
        jdbc.update("delete from platform_integrations where id = ?", id);
        audit.record("INTEGRATION_DELETED", "INTEGRATION", id, null, "Deleted integration " + i.name());
    }

    /** Calls the integration's endpoint once. Private and local addresses are refused. */
    public IntegrationRow testIntegration(Long id) {
        IntegrationRow i = integration(id);
        if (i.endpoint() == null) throw ApiException.badRequest("Add an endpoint URL to this integration to test it.");
        String status, msg;
        int ms = 0;
        try {
            URI u = URI.create(i.endpoint());
            for (InetAddress a : InetAddress.getAllByName(u.getHost())) {
                byte[] bb = a.getAddress();
                boolean uniqueLocal = bb.length == 16 && (bb[0] & 0xFE) == 0xFC;
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress() || uniqueLocal) throw new IOException("Private or local addresses cannot be tested.");
            }
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
            long t0 = System.nanoTime();
            HttpResponse<Void> r = c.send(HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.discarding());
            ms = (int) ((System.nanoTime() - t0) / 1_000_000);
            boolean ok = r.statusCode() < 400;
            status = ok ? "ACTIVE" : "ISSUE";
            msg = "HTTP " + r.statusCode() + " in " + ms + " ms";
        } catch (IOException e) {
            status = "ISSUE"; msg = e.getMessage() == null ? "Could not reach the endpoint." : "Could not reach the endpoint: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status = "ISSUE"; msg = "Test interrupted.";
        } catch (RuntimeException e) {
            status = "ISSUE"; msg = "Could not test: " + e.getClass().getSimpleName();
        }
        if (msg.length() > 300) msg = msg.substring(0, 300);
        jdbc.update("update platform_integrations set status = ?, last_run = now(), last_ms = ?, last_message = ? where id = ? and status <> 'DISABLED'", status, ms == 0 ? null : ms, msg, id);
        return integration(id);
    }

    // ------------------------------------------------------------------ data management

    private List<DataRow> dataRows() {
        List<DataRow> rows = new ArrayList<>();
        rows.add(data("STUDENTS", "Students", "select count(*), max(created_at) from students"));
        rows.add(data("TEACHERS", "Teachers", "select count(*), null::timestamptz from teachers"));
        rows.add(data("PARENTS", "Parents", "select count(*), max(created_at) from users where user_type = 'PARENT'"));
        rows.add(data("ACADEMIC", "Academic Data", "select (select count(*) from school_classes) + (select count(*) from sections) + (select count(*) from subjects) + (select count(*) from terms), null::timestamptz"));
        rows.add(data("FINANCE", "Finance Data", "select (select count(*) from payments) + (select count(*) from invoices), (select max(created_at) from payments)"));
        return rows;
    }

    private DataRow data(String code, String name, String sql) {
        return jdbc.query(sql, rs -> { rs.next(); long n = rs.getLong(1); return new DataRow(code, name, n, iso(rs.getTimestamp(2)), n > 0 ? "AVAILABLE" : "EMPTY"); });
    }

    @Transactional(readOnly = true)
    public byte[] exportData(String type) {
        String t = type == null ? "" : type.toUpperCase();
        String sql = switch (t) {
            case "STUDENTS" -> "select s.admission_no \"Admission No\", s.full_name \"Student\", coalesce(c.name,'') \"Class\", coalesce(sec.name,'') \"Section\", i.name \"Institution\", s.active \"Active\" from students s join institutions i on i.id = s.institution_id left join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id order by i.name, s.full_name";
            case "TEACHERS" -> "select t.full_name \"Teacher\", coalesce(t.email,'') \"Email\", coalesce(t.phone,'') \"Phone\", i.name \"Institution\", t.active \"Active\" from teachers t join institutions i on i.id = t.institution_id order by i.name, t.full_name";
            case "PARENTS" -> "select u.full_name \"Parent\", u.email \"Email\", coalesce(i.name,'') \"Institution\", u.status \"Status\" from users u left join institutions i on i.id = u.institution_id where u.user_type = 'PARENT' order by u.full_name";
            case "ACADEMIC" -> "select i.name \"Institution\", c.name \"Class\", sec.name \"Section\", (select count(*) from students s where s.section_id = sec.id and s.active) \"Students\" from sections sec join school_classes c on c.id = sec.school_class_id join institutions i on i.id = sec.institution_id order by i.name, c.name, sec.name";
            case "FINANCE" -> "select i.name \"Institution\", p.paid_on \"Paid On\", p.amount \"Amount\", p.method \"Method\", coalesce(p.plan_type,'') \"Plan\", coalesce(p.reference,'') \"Reference\" from payments p join institutions i on i.id = p.institution_id order by p.paid_on desc";
            default -> throw ApiException.badRequest("Unknown data type.");
        };
        StringBuilder csv = new StringBuilder("﻿");
        jdbc.query(sql, (org.springframework.jdbc.core.ResultSetExtractor<Object>) rs -> {
            int cols = rs.getMetaData().getColumnCount();
            for (int c = 1; c <= cols; c++) { if (c > 1) csv.append(','); csv.append(q(rs.getMetaData().getColumnLabel(c))); }
            csv.append('\n');
            while (rs.next()) {
                for (int c = 1; c <= cols; c++) { if (c > 1) csv.append(','); Object o = rs.getObject(c); csv.append(q(o == null ? "" : o.toString())); }
                csv.append('\n');
            }
            return null;
        });
        audit.record("DATA_EXPORTED", "DATA", null, null, "Exported " + t.toLowerCase() + " data");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String q(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------ monitoring

    @Transactional
    public List<ServiceRow> runChecks() {
        HealthService.Snapshot s = health.checkAndLog();
        return monitoring(s);
    }

    private List<ServiceRow> monitoring(HealthService.Snapshot snap) {
        Double[] up = new Double[3];
        jdbc.query("select avg(case when application_ok then 100.0 else 0 end), avg(case when database_ok then 100.0 else 0 end), avg(case when storage_ok then 100.0 else 0 end), count(*) from health_logs where checked_at > now() - interval '30 days'", rs -> {
            if (rs.getLong(4) > 0) for (int i = 0; i < 3; i++) { double v = rs.getDouble(i + 1); up[i] = Math.round(v * 100) / 100.0; }
        });
        List<ServiceRow> out = new ArrayList<>();
        for (HealthService.Check c : snap.checks()) {
            Double uptime = c.name().equals("Application") ? up[0] : c.name().equals("Database") ? up[1] : c.name().equals("File Storage") ? up[2] : null;
            Integer ms = null;
            java.util.regex.Matcher m = Pattern.compile("Responded in (\\d+) ms").matcher(c.detail());
            if (m.find()) ms = Integer.valueOf(m.group(1));
            String st = c.status().equals("OPERATIONAL") ? "HEALTHY" : c.status().equals("DEGRADED") ? "DEGRADED" : c.status().equals("DOWN") ? "DOWN" : "NOT_CONFIGURED";
            out.add(new ServiceRow(c.name().equals("Application") ? "Application Server" : c.name(), st, uptime, ms, snap.checkedAt().toString(), c.detail()));
        }
        for (IntegrationRow i : integrations()) {
            if (i.status().equals("DISABLED")) continue;
            out.add(new ServiceRow(i.name(), i.lastRun() == null ? "NOT_CONFIGURED" : i.status().equals("ACTIVE") ? "HEALTHY" : "DEGRADED", null, i.lastMs(), i.lastRun(), i.message() == null ? "Not tested yet" : i.message()));
        }
        return out;
    }

    // ------------------------------------------------------------------ backups

    private static final String BK = "select id, backup_type, trigger, status, size_bytes, table_count, row_count, note, created_by, created_at from system_backups";

    private BackupRow bk(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BackupRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getLong(7), rs.getString(8), rs.getString(9), iso(rs.getTimestamp(10)));
    }

    private List<BackupRow> backups() { return jdbc.query(BK + " order by created_at desc, id desc limit 30", (rs, n) -> bk(rs)); }

    private static boolean isConfig(String t) {
        return t.startsWith("platform_") || t.startsWith("system_") || t.endsWith("_settings") || t.equals("institution_modules") || t.equals("catalog_items") || t.equals("kb_articles");
    }

    public BackupRow runBackup(BackupBody b) {
        String type = b.type() == null ? "FULL" : b.type().trim().toUpperCase();
        if (!BACKUP_TYPES.contains(type)) throw new FieldErrorsException(Map.of("type", "Choose a backup type."));
        byte[] zip = new byte[0];
        int tables = 0;
        long rows = 0;
        String status = "SUCCESS", note = "CSV export of the selected tables. Passwords, secrets and file contents are not included.";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            StringBuilder manifest = new StringBuilder("Classzy backup (" + type + ")\nCreated: " + Instant.now() + "\n\n");
            try (ZipOutputStream z = new ZipOutputStream(bos)) {
                List<String> names = jdbc.queryForList("select table_name from information_schema.tables where table_schema = current_schema() and table_type = 'BASE TABLE' order by table_name", String.class);
                for (String t : names) {
                    if (SKIP_TABLES.contains(t)) continue;
                    boolean cfg = isConfig(t);
                    if ((type.equals("CONFIGURATION") && !cfg) || (type.equals("DATABASE") && cfg)) continue;
                    List<String> cols = jdbc.queryForList("select column_name from information_schema.columns where table_schema = current_schema() and table_name = ? and data_type <> 'bytea' order by ordinal_position", String.class, t)
                            .stream().filter(c -> !SECRETISH.matcher(c).matches()).toList();
                    if (cols.isEmpty()) continue;
                    String sel = cols.stream().map(c -> "\"" + c.replace("\"", "\"\"") + "\"").collect(Collectors.joining(", "));
                    StringBuilder csv = new StringBuilder("﻿").append(cols.stream().map(SysService::q).collect(Collectors.joining(","))).append('\n');
                    long[] n = { 0 };
                    jdbc.query("select " + sel + " from \"" + t.replace("\"", "\"\"") + "\"", rs -> {
                        for (int c = 1; c <= cols.size(); c++) { if (c > 1) csv.append(','); Object o = rs.getObject(c); csv.append(q(o == null ? "" : o.toString())); }
                        csv.append('\n'); n[0]++;
                        if (csv.length() > MAX_BACKUP) throw new IllegalStateException("The data is too large for an in-app backup.");
                    });
                    z.putNextEntry(new ZipEntry(t + ".csv"));
                    z.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                    z.closeEntry();
                    manifest.append(t).append(": ").append(n[0]).append(" rows\n");
                    tables++; rows += n[0];
                    if (bos.size() > MAX_BACKUP) throw new IllegalStateException("The data is too large for an in-app backup.");
                }
                z.putNextEntry(new ZipEntry("MANIFEST.txt"));
                z.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            zip = bos.toByteArray();
        } catch (IOException | RuntimeException e) {
            status = "FAILED"; note = e.getMessage() == null ? "Backup failed." : e.getMessage();
            if (note.length() > 300) note = note.substring(0, 300);
            zip = new byte[0]; tables = 0; rows = 0;
        }
        Long id = jdbc.queryForObject("insert into system_backups (backup_type, status, size_bytes, table_count, row_count, note, data, created_by) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, type, status, zip.length, tables, rows, note, zip, actor());
        int keep = Integer.parseInt(values(CONFIG).get("backupRetentionDays"));
        jdbc.update("delete from system_backups where created_at < now() - (? * interval '1 day') and id <> ?", keep, id);
        audit.record("BACKUP_" + status, "BACKUP", id, null, type + " backup " + (status.equals("SUCCESS") ? "completed (" + tables + " tables)" : "failed"));
        return jdbc.query(BK + " where id = ?", (rs, n) -> bk(rs), id).get(0);
    }

    @Transactional(readOnly = true)
    public Object[] backupData(Long id) {
        List<Object[]> r = jdbc.query("select backup_type, status, created_at, data from system_backups where id = ?", (rs, n) -> new Object[] { rs.getString(1), rs.getString(2), rs.getTimestamp(3), rs.getBytes(4) }, id);
        if (r.isEmpty()) throw ApiException.notFound("Backup");
        if (!"SUCCESS".equals(r.get(0)[1])) throw ApiException.conflict("This backup failed and has no file.");
        audit.record("BACKUP_DOWNLOADED", "BACKUP", id, null, "Downloaded " + r.get(0)[0] + " backup");
        return r.get(0);
    }

    public void deleteBackup(Long id) {
        if (jdbc.update("delete from system_backups where id = ?", id) == 0) throw ApiException.notFound("Backup");
        audit.record("BACKUP_DELETED", "BACKUP", id, null, "Deleted backup");
    }
}
