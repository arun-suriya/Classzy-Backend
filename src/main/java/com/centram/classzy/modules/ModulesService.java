package com.centram.classzy.modules;

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
import com.centram.classzy.modules.ModulesDto.AccessRow;
import com.centram.classzy.modules.ModulesDto.AuditItem;
import com.centram.classzy.modules.ModulesDto.Detail;
import com.centram.classzy.modules.ModulesDto.FeatureRow;
import com.centram.classzy.modules.ModulesDto.MetaBody;
import com.centram.classzy.modules.ModulesDto.ModuleRow;
import com.centram.classzy.modules.ModulesDto.Overview;
import com.centram.classzy.modules.ModulesDto.SaveBody;
import com.centram.classzy.modules.ModulesDto.Stats;
import com.centram.classzy.modules.ModulesDto.Usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The platform's module catalog: which modules and features exist, which are switched on, and which institutions use them. */
@Service
@Transactional
public class ModulesService {

    static final Set<String> CATEGORIES = Set.of("CORE", "ACADEMIC", "ADMINISTRATION", "COMMUNICATION", "FINANCE", "INTEGRATION", "REPORTS");
    private static final Map<String, Spec> SETTINGS = new LinkedHashMap<>();

    static {
        SETTINGS.put("defaultStatus", new Spec("defaultStatus", Type.BOOL, "true", false, 0, 0, List.of()));
        SETTINGS.put("allowCustomFields", new Spec("allowCustomFields", Type.BOOL, "true", false, 0, 0, List.of()));
        SETTINGS.put("maxAttachments", new Spec("maxAttachments", Type.INT, "10", false, 1, 100, List.of()));
        SETTINGS.put("retentionYears", new Spec("retentionYears", Type.INT, "5", false, 1, 50, List.of()));
    }

    private static final String ROWS = "select m.code, m.name, m.category, m.description, m.icon, m.enabled, "
            + "(select count(*) from platform_features f where f.module_code = m.code and f.enabled), (select count(*) from platform_features f where f.module_code = m.code), "
            + "(select count(*) from institution_modules im join institutions i on i.id = im.institution_id where im.module_code = m.code and i.lifecycle <> 'ARCHIVED') from platform_modules m";

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ModulesService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static ModuleRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ModuleRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getInt(7), rs.getInt(8), rs.getLong(9));
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        List<ModuleRow> rows = jdbc.query(ROWS + " order by m.position", (rs, i) -> row(rs));
        int enabled = (int) rows.stream().filter(ModuleRow::enabled).count();
        int features = rows.stream().mapToInt(ModuleRow::featuresTotal).sum(), featuresOn = rows.stream().mapToInt(ModuleRow::featuresEnabled).sum();
        long using = jdbc.queryForObject("select count(distinct im.institution_id) from institution_modules im join institutions i on i.id = im.institution_id "
                + "where i.lifecycle <> 'ARCHIVED' and im.module_code in (select code from platform_modules)", Long.class);
        long usingNew = jdbc.queryForObject("select count(distinct im.institution_id) from institution_modules im join institutions i on i.id = im.institution_id "
                + "where i.lifecycle <> 'ARCHIVED' and im.module_code in (select code from platform_modules) and i.created_at >= date_trunc('month', now())", Long.class);
        long custom = jdbc.queryForObject("select (select count(*) from platform_features where enabled <> default_enabled) + (select count(*) from platform_module_settings)", Long.class);
        long updated = jdbc.queryForObject("select count(*) from audit_logs where entity_type like 'MODULE\\_%' and action = 'MODULE_UPDATED' and created_at >= date_trunc('month', now())", Long.class);
        return new Overview(new Stats(rows.size(), enabled, features, featuresOn, using, usingNew, custom, updated), rows);
    }

    @Transactional(readOnly = true)
    public Detail detail(String code) {
        ModuleRow m = find(code);
        String noun = jdbc.queryForObject("select record_noun from platform_modules where code = ?", String.class, code);
        List<FeatureRow> features = jdbc.query("select code, name, description, enabled, default_enabled from platform_features where module_code = ? order by position",
                (rs, i) -> new FeatureRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5)), code);
        return new Detail(m, noun, features, settings(code));
    }

    public Detail save(String code, SaveBody b) {
        ModuleRow m = find(code);
        Map<String, String> err = new LinkedHashMap<>();
        Map<String, String> clean = new LinkedHashMap<>();
        if (b.settings() != null) {
            for (var e : b.settings().entrySet()) {
                Spec sp = SETTINGS.get(e.getKey());
                if (sp == null) { err.put(e.getKey(), "Unknown setting."); continue; }
                try { String v = CommSpec.clean(sp, e.getValue()); if (v != null) clean.put(sp.key(), v); } catch (IllegalArgumentException ex) { err.put(sp.key(), ex.getMessage()); }
            }
        }
        List<String> known = jdbc.queryForList("select code from platform_features where module_code = ?", String.class, code);
        if (b.features() != null) for (String k : b.features().keySet()) if (!known.contains(k)) err.put("features", "Unknown feature " + k + ".");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        if (b.features() != null) b.features().forEach((k, v) -> jdbc.update("update platform_features set enabled = ? where module_code = ? and code = ?", Boolean.TRUE.equals(v), code, k));
        clean.forEach((k, v) -> jdbc.update("insert into platform_module_settings (module_code, setting_key, setting_value) values (?, ?, ?) on conflict (module_code, setting_key) do update set setting_value = excluded.setting_value", code, k, v));
        audit.record("MODULE_UPDATED", "MODULE_" + code, null, null, m.name() + " configuration updated");
        return detail(code);
    }

    public Detail meta(String code, MetaBody b) {
        ModuleRow m = find(code);
        Map<String, String> err = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim(), desc = b.description() == null ? "" : b.description().trim();
        String cat = b.category() == null ? "" : b.category().trim().toUpperCase();
        if (name.isEmpty()) err.put("name", "Required."); else if (name.length() > 80) err.put("name", "Use 80 characters or fewer.");
        if (desc.isEmpty()) err.put("description", "Required."); else if (desc.length() > 300) err.put("description", "Use 300 characters or fewer.");
        if (!CATEGORIES.contains(cat)) err.put("category", "Choose a category.");
        if (!err.containsKey("name") && jdbc.queryForObject("select count(*) from platform_modules where lower(name) = lower(?) and code <> ?", Integer.class, name, code) > 0) err.put("name", "Another module already has this name.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        jdbc.update("update platform_modules set name = ?, category = ?, description = ? where code = ?", name, cat, desc, code);
        audit.record("MODULE_UPDATED", "MODULE_" + code, null, null, m.name() + " details updated");
        return detail(code);
    }

    public ModuleRow setEnabled(String code, boolean enabled) {
        ModuleRow m = find(code);
        if (m.enabled() != enabled) {
            jdbc.update("update platform_modules set enabled = ? where code = ?", enabled, code);
            audit.record("MODULE_STATUS_CHANGED", "MODULE_" + code, null, null, m.name() + (enabled ? " enabled" : " disabled"));
        }
        return find(code);
    }

    public Overview bulk(Map<String, Boolean> states) {
        if (states == null || states.isEmpty()) throw ApiException.badRequest("Nothing to change.");
        states.keySet().forEach(this::find);
        states.forEach((code, on) -> setEnabled(code, Boolean.TRUE.equals(on)));
        return overview();
    }

    public Detail reset(String code) {
        ModuleRow m = find(code);
        jdbc.update("update platform_features set enabled = default_enabled where module_code = ?", code);
        jdbc.update("delete from platform_module_settings where module_code = ?", code);
        audit.record("MODULE_UPDATED", "MODULE_" + code, null, null, m.name() + " reset to defaults");
        return detail(code);
    }

    @Transactional(readOnly = true)
    public List<AccessRow> access(String code) {
        find(code);
        return jdbc.query("select i.id, i.name, exists (select 1 from institution_modules im where im.institution_id = i.id and im.module_code = ?) from institutions i where i.lifecycle <> 'ARCHIVED' order by lower(i.name)",
                (rs, n) -> new AccessRow(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), code);
    }

    public AccessRow setAccess(String code, Long institutionId, boolean enabled) {
        ModuleRow m = find(code);
        List<String> names = jdbc.queryForList("select name from institutions where id = ? and lifecycle <> 'ARCHIVED'", String.class, institutionId);
        if (names.isEmpty()) throw ApiException.notFound("Institution");
        if (enabled) {
            if (!m.enabled()) throw ApiException.conflict("This module is disabled platform-wide. Enable it first.");
            jdbc.update("insert into institution_modules (institution_id, module_code) values (?, ?) on conflict do nothing", institutionId, code);
        } else {
            jdbc.update("delete from institution_modules where institution_id = ? and module_code = ?", institutionId, code);
        }
        audit.record("MODULE_ACCESS_CHANGED", "MODULE_" + code, null, institutionId, m.name() + (enabled ? " granted to " : " removed from ") + names.get(0));
        return new AccessRow(institutionId, names.get(0), enabled);
    }

    @Transactional(readOnly = true)
    public Usage usage(String code) {
        ModuleRow m = find(code);
        long total = jdbc.queryForObject("select count(*) from institutions where lifecycle <> 'ARCHIVED'", Long.class);
        List<java.sql.Timestamp> last = jdbc.queryForList("select max(created_at) from audit_logs where entity_type = ?", java.sql.Timestamp.class, "MODULE_" + code);
        return new Usage(m.institutions(), total, m.featuresEnabled(), m.featuresTotal(), last.isEmpty() || last.get(0) == null ? null : last.get(0).toInstant().toString());
    }

    @Transactional(readOnly = true)
    public List<AuditItem> auditLog(String code) {
        find(code);
        return jdbc.query("select created_at, actor_email, description from audit_logs where entity_type = ? order by created_at desc limit 50",
                (rs, n) -> new AuditItem(rs.getTimestamp(1).toInstant().toString(), rs.getString(2), rs.getString(3)), "MODULE_" + code);
    }

    private Map<String, String> settings(String code) {
        Map<String, String> stored = new LinkedHashMap<>();
        jdbc.query("select setting_key, setting_value from platform_module_settings where module_code = ?", rs -> { stored.put(rs.getString(1), rs.getString(2)); }, code);
        Map<String, String> out = new LinkedHashMap<>();
        SETTINGS.forEach((k, sp) -> out.put(k, stored.getOrDefault(k, sp.def())));
        return out;
    }

    private ModuleRow find(String code) {
        List<ModuleRow> r = jdbc.query(ROWS + " where m.code = ?", (rs, i) -> row(rs), code);
        if (r.isEmpty()) throw ApiException.notFound("Module");
        return r.get(0);
    }
}
