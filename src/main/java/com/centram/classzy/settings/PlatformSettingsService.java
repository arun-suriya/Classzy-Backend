package com.centram.classzy.settings;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.communication.CommSpec;
import com.centram.classzy.communication.CommSpec.Spec;
import com.centram.classzy.communication.CommSpec.Type;
import com.centram.classzy.security.AppPrincipal;
import com.centram.classzy.sysmgmt.SysService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Platform-wide settings (Super Admin "Settings" page). Values are stored, not enforced.
 * Four settings (2FA, SSO, session timeout, password length, maintenance mode) belong to System Management
 * and are read and written there, so the two pages never disagree.
 */
@Service
@Transactional
public class PlatformSettingsService {

    private static final String SECTION = "PLATFORM";
    private static final Map<String, Spec> OWN = new LinkedHashMap<>();
    private static final Set<String> SHARED_SECURITY = Set.of("mfaRequired", "ssoEnabled", "sessionTimeoutMinutes", "passwordMinLength");
    private static final Set<String> SHARED_CONFIG = Set.of("maintenanceMode");

    public static final List<String> LANGUAGES = List.of("en-US", "en-GB", "en-IN", "hi-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN", "mr-IN", "bn-IN");
    public static final List<String> TIME_ZONES = List.of("Asia/Kolkata", "Asia/Dubai", "Asia/Singapore", "Asia/Dhaka", "Asia/Kathmandu", "Asia/Colombo", "Europe/London", "America/New_York", "America/Los_Angeles", "Australia/Sydney", "UTC");
    public static final List<String> DATE_FORMATS = List.of("DD MMM YYYY", "DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD");
    public static final List<String> LANDING = List.of("DASHBOARD", "INSTITUTIONS", "USERS", "REPORTS", "SUPPORT");
    static final long MAX_ASSET = 2L * 1024 * 1024;
    static final Map<String, Set<String>> ASSET_TYPES = Map.of(
            "LOGO", Set.of("image/png", "image/jpeg", "image/webp"),
            "FAVICON", Set.of("image/png", "image/x-icon", "image/vnd.microsoft.icon", "image/jpeg"));

    private static void add(String k, Type t, String def, boolean req, int min, int max, List<String> allowed) { OWN.put(k, new Spec(k, t, def, req, min, max, allowed)); }

    static {
        add("platformName", Type.TEXT, "Classzy", true, 0, 80, List.of());
        add("supportEmail", Type.EMAIL, "", false, 0, 0, List.of());
        add("defaultLanguage", Type.OPT, "en-US", false, 0, 0, LANGUAGES);
        add("timeZone", Type.TZ, "Asia/Kolkata", false, 0, 0, List.of());
        add("dateFormat", Type.OPT, "DD MMM YYYY", false, 0, 0, DATE_FORMATS);
        add("timeFormat", Type.OPT, "12H", false, 0, 0, List.of("12H", "24H"));
        add("itemsPerPage", Type.INT, "25", false, 5, 200, List.of());
        add("primaryColor", Type.COLOR, "#2563EB", false, 0, 0, List.of());
        add("secondaryColor", Type.COLOR, "#10B981", false, 0, 0, List.of());
        add("accentColor", Type.COLOR, "#F59E0B", false, 0, 0, List.of());
        add("emailEnabled", Type.BOOL, "true", false, 0, 0, List.of());
        add("fromEmail", Type.EMAIL, "", false, 0, 0, List.of());
        add("replyToEmail", Type.EMAIL, "", false, 0, 0, List.of());
        add("emailTemplate", Type.OPT, "DEFAULT", false, 0, 0, List.of("DEFAULT", "MINIMAL", "BRANDED"));
        add("strongPasswordPolicy", Type.BOOL, "true", false, 0, 0, List.of());
        add("ipAllowlistEnabled", Type.BOOL, "false", false, 0, 0, List.of());
        add("auditLoginActivity", Type.BOOL, "true", false, 0, 0, List.of());
        add("sessionTimeoutWarning", Type.BOOL, "true", false, 0, 0, List.of());
        add("systemNotifications", Type.BOOL, "true", false, 0, 0, List.of());
        add("usageAnalytics", Type.BOOL, "false", false, 0, 0, List.of());
        add("feedbackWidget", Type.BOOL, "true", false, 0, 0, List.of());
        add("defaultLandingPage", Type.OPT, "DASHBOARD", false, 0, 0, LANDING);
    }

    public record Asset(boolean present, String fileName, long size, String updatedAt) {}
    public record Settings(Map<String, String> values, Asset logo, Asset favicon, Map<String, List<String>> options, String updatedAt) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SysService sys;

    public PlatformSettingsService(JdbcTemplate jdbc, AuditService audit, SysService sys) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.sys = sys;
    }

    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }

    private String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppPrincipal p ? p.email() : null;
    }

    @Transactional(readOnly = true)
    public Settings get() {
        Map<String, String> stored = new LinkedHashMap<>();
        jdbc.query("select setting_key, setting_value from platform_settings where section = ?", rs -> { stored.put(rs.getString(1), rs.getString(2)); }, SECTION);
        Map<String, String> out = new LinkedHashMap<>();
        OWN.forEach((k, sp) -> out.put(k, stored.getOrDefault(k, sp.def())));
        out.putAll(sys.securityValues());
        out.put("maintenanceMode", sys.configValues().get("maintenanceMode"));
        out.keySet().retainAll(allKeys());
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("languages", LANGUAGES);
        options.put("timeZones", TIME_ZONES);
        options.put("dateFormats", DATE_FORMATS);
        options.put("landingPages", LANDING);
        return new Settings(out, asset("LOGO"), asset("FAVICON"), options, lastUpdated());
    }

    private Set<String> allKeys() {
        Set<String> k = new java.util.LinkedHashSet<>(OWN.keySet());
        k.addAll(SHARED_SECURITY);
        k.addAll(SHARED_CONFIG);
        return k;
    }

    private String lastUpdated() {
        return iso(jdbc.queryForObject("select max(t) from (select max(updated_at) t from platform_assets union all select max(updated_at) from system_settings) x", Timestamp.class));
    }

    private Asset asset(String kind) {
        List<Asset> r = jdbc.query("select file_name, size_bytes, updated_at from platform_assets where kind = ?", (rs, i) -> new Asset(true, rs.getString(1), rs.getLong(2), iso(rs.getTimestamp(3))), kind);
        return r.isEmpty() ? new Asset(false, null, 0, null) : r.get(0);
    }

    public Settings save(Map<String, String> body) {
        if (body == null || body.isEmpty()) throw ApiException.badRequest("Nothing to save.");
        Map<String, String> err = new LinkedHashMap<>(), clean = new LinkedHashMap<>(), sec = new LinkedHashMap<>(), cfg = new LinkedHashMap<>();
        for (var e : body.entrySet()) {
            String k = e.getKey();
            if (SHARED_SECURITY.contains(k)) { sec.put(k, e.getValue()); continue; }
            if (SHARED_CONFIG.contains(k)) { cfg.put(k, e.getValue()); continue; }
            Spec sp = OWN.get(k);
            if (sp == null) { err.put(k, "Unknown setting."); continue; }
            try {
                String v = CommSpec.clean(sp, e.getValue());
                clean.put(k, v == null ? "" : v);
            } catch (IllegalArgumentException ex) { err.put(k, ex.getMessage()); }
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        clean.forEach((k, v) -> jdbc.update("insert into platform_settings (section, setting_key, setting_value) values (?, ?, ?) on conflict (section, setting_key) do update set setting_value = excluded.setting_value", SECTION, k, v));
        if (!sec.isEmpty()) sys.saveSettings(true, sec);
        if (!cfg.isEmpty()) sys.saveSettings(false, cfg);
        if (!clean.isEmpty()) jdbc.update("insert into system_settings (setting_key, setting_value, updated_by) values ('platformSettingsSavedAt', now()::text, ?) on conflict (setting_key) do update set setting_value = excluded.setting_value, updated_at = now(), updated_by = excluded.updated_by", actor());
        audit.record("PLATFORM_SETTINGS_UPDATED", "PLATFORM_SETTINGS", null, null, "Platform settings updated: " + String.join(", ", body.keySet()));
        return get();
    }

    public Asset upload(String kind, MultipartFile file) {
        Set<String> types = ASSET_TYPES.get(kind);
        if (types == null) throw ApiException.notFound("Asset");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose an image to upload.");
        if (file.getSize() > MAX_ASSET) throw ApiException.badRequest("Images can be up to 2 MB.");
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!types.contains(ct)) throw ApiException.badRequest(kind.equals("LOGO") ? "Use a PNG, JPG or WebP image." : "Use an ICO, PNG or JPG image.");
        byte[] data;
        try { data = file.getBytes(); } catch (java.io.IOException e) { throw ApiException.badRequest("Couldn't read that file."); }
        String name = file.getOriginalFilename() == null ? kind.toLowerCase() : file.getOriginalFilename().replaceAll("[\\\\/]+", "_").trim();
        if (name.isEmpty()) name = kind.toLowerCase();
        if (name.length() > 200) name = name.substring(name.length() - 200);
        jdbc.update("insert into platform_assets (kind, file_name, content_type, size_bytes, data, updated_by) values (?, ?, ?, ?, ?, ?) on conflict (kind) do update set file_name = excluded.file_name, content_type = excluded.content_type, size_bytes = excluded.size_bytes, data = excluded.data, updated_at = now(), updated_by = excluded.updated_by",
                kind, name, ct, data.length, data, actor());
        audit.record("PLATFORM_ASSET_UPDATED", "PLATFORM_SETTINGS", null, null, kind + " replaced: " + name);
        return asset(kind);
    }

    /** {contentType, bytes} */
    @Transactional(readOnly = true)
    public Object[] assetData(String kind) {
        List<Object[]> r = jdbc.query("select content_type, data from platform_assets where kind = ?", (rs, i) -> new Object[] { rs.getString(1), rs.getBytes(2) }, kind);
        if (r.isEmpty()) throw ApiException.notFound("Image");
        return r.get(0);
    }

    public void removeAsset(String kind) {
        if (!ASSET_TYPES.containsKey(kind)) throw ApiException.notFound("Asset");
        if (jdbc.update("delete from platform_assets where kind = ?", kind) > 0) audit.record("PLATFORM_ASSET_REMOVED", "PLATFORM_SETTINGS", null, null, kind + " removed");
    }
}
