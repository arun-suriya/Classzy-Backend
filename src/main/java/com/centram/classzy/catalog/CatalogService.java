package com.centram.classzy.catalog;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.catalog.CatalogDto.ItemBody;
import com.centram.classzy.catalog.CatalogDto.ItemView;
import com.centram.classzy.catalog.CatalogSpec.Kind;
import com.centram.classzy.catalog.CatalogSpec.Seed;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.communication.CommDto.TestResult;
import com.centram.classzy.communication.CommSettingsStore;
import com.centram.classzy.communication.CommSpec;
import com.centram.classzy.communication.CommSpec.Spec;
import com.centram.classzy.communication.CommSpec.Type;
import com.centram.classzy.communication.NetProbe;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generic lists kept per institution (library categories, statuses and rules, custom fields, integrations). */
@Service
@Transactional
public class CatalogService {

    private static final Pattern FIELD_CODE = Pattern.compile("[a-z][a-z0-9_]{1,39}");

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final CommSettingsStore store;
    private final AuditService audit;

    public CatalogService(JdbcTemplate jdbc, CurrentUser current, CommSettingsStore store, AuditService audit) {
        this.jdbc = jdbc;
        this.current = current;
        this.store = store;
        this.audit = audit;
    }

    public List<ItemView> list(String kindName) {
        Kind kind = kind(kindName);
        Long inst = current.institutionId();
        if (!kind.seeds().isEmpty() && store.mark(inst, "catalog." + kindName)) {
            int pos = 0;
            for (Seed s : kind.seeds()) {
                String code = s.code();
                if (code == null && kind.autoPrefix() != null) code = nextCode(inst, kindName, kind.autoPrefix());
                insert(inst, kindName, s.name(), code, true, pos++, s.attrs());
            }
        }
        return views(inst, kindName, null);
    }

    public ItemView create(String kindName, ItemBody body) {
        Kind kind = kind(kindName);
        Long inst = current.institutionId();
        Prepared p = prepare(inst, kindName, kind, body, null, Map.of());
        int pos = jdbc.queryForObject("select coalesce(max(position), -1) + 1 from catalog_items where institution_id = ? and kind = ?", Integer.class, inst, kindName);
        long id = insert(inst, kindName, p.name, p.code, p.active, pos, p.attrs);
        audit.record("CATALOG_CREATED", kind.label(), id, inst, kind.label() + " \"" + p.name + "\" added");
        return views(inst, kindName, id).get(0);
    }

    public ItemView update(String kindName, Long id, ItemBody body) {
        Kind kind = kind(kindName);
        Long inst = current.institutionId();
        Map<String, String> existing = attrsOf(inst, kindName, id);
        Prepared p = prepare(inst, kindName, kind, body, id, existing);
        try {
            jdbc.update("update catalog_items set name = ?, code = ?, active = ? where id = ?", p.name, p.code, p.active, id);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw duplicate(kind);
        }
        jdbc.update("delete from catalog_item_attrs where item_id = ? and attr_key not like '\\_%'", id);
        p.attrs.forEach((k, v) -> putAttr(id, k, v));
        audit.record("CATALOG_UPDATED", kind.label(), id, inst, kind.label() + " \"" + p.name + "\" updated");
        return views(inst, kindName, id).get(0);
    }

    public void delete(String kindName, Long id) {
        Kind kind = kind(kindName);
        Long inst = current.institutionId();
        String name = nameOf(inst, kindName, id);
        if ("LIB_CATEGORY".equals(kindName)) {
            Integer children = jdbc.queryForObject("select count(*) from catalog_item_attrs a join catalog_items i on i.id = a.item_id "
                    + "where i.institution_id = ? and i.kind = 'LIB_CATEGORY' and a.attr_key = 'parent' and a.attr_value = ?", Integer.class, inst, String.valueOf(id));
            if (children != null && children > 0) throw ApiException.conflict("This category has sub-categories. Remove or move them first.");
        }
        jdbc.update("delete from catalog_items where id = ?", id);
        audit.record("CATALOG_DELETED", kind.label(), id, inst, kind.label() + " \"" + name + "\" removed");
    }

    /** Reachability check for an integration's base address. Credentials are not verified. */
    public ItemView test(Long id) {
        Long inst = current.institutionId();
        nameOf(inst, "INTEGRATION", id);
        Map<String, String> a = attrsOf(inst, "INTEGRATION", id);
        String url = a.getOrDefault("baseUrl", "");
        TestResult r;
        if (url.isBlank()) {
            r = new TestResult(false, "Add a base URL first.");
        } else {
            try {
                URI u = URI.create(url);
                int port = u.getPort() > 0 ? u.getPort() : ("http".equalsIgnoreCase(u.getScheme()) ? 80 : 443);
                r = NetProbe.probe(u.getHost(), port);
            } catch (IllegalArgumentException e) {
                r = new TestResult(false, "The base URL is not valid.");
            }
        }
        putAttr(id, "_status", r.ok() ? "REACHABLE" : "UNREACHABLE");
        putAttr(id, "_message", r.message());
        putAttr(id, "_checked", java.time.LocalDateTime.now().withNano(0).toString());
        audit.record("INTEGRATION_TESTED", "Integration", id, inst, "Integration connection tested");
        return views(inst, "INTEGRATION", id).get(0);
    }

    // ---- internals
    private record Prepared(String name, String code, boolean active, Map<String, String> attrs) {}

    private Prepared prepare(Long inst, String kindName, Kind kind, ItemBody body, Long selfId, Map<String, String> existing) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = body.name() == null ? "" : body.name().trim();
        if (name.isEmpty()) err.put("name", "Required.");
        else if (name.length() > 100) err.put("name", "Use 100 characters or fewer.");
        else if (kind.nameIsFixedList() && !CatalogSpec.MEMBER_TYPES.contains(name)) err.put("name", "Choose a member type.");

        String code = body.code() == null || body.code().isBlank() ? null : body.code().trim();
        if (code == null && kind.codeRequired()) err.put("code", "Required.");
        if (code != null && code.length() > 40) err.put("code", "Use 40 characters or fewer.");
        if (code != null && "CUSTOM_FIELD".equals(kindName) && !FIELD_CODE.matcher(code).matches())
            err.put("code", "Use lowercase letters, numbers and underscores, starting with a letter.");
        if (code == null && selfId != null) code = codeOf(selfId);
        if (code == null && kind.autoPrefix() != null) code = nextCode(inst, kindName, kind.autoPrefix());

        Map<String, String> in = body.attrs() == null ? Map.of() : body.attrs();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            Spec sp = kind.attrs().get(e.getKey());
            if (sp == null) { if (!e.getKey().startsWith("_")) err.put(e.getKey(), "Unknown field."); continue; }
            try {
                String v = CommSpec.clean(sp, e.getValue());
                if (v == null) { v = existing.get(sp.key()); }   // secret left as-is
                if (v != null && !v.isBlank()) out.put(sp.key(), v);
            } catch (IllegalArgumentException ex) {
                err.put(sp.key(), ex.getMessage());
            }
        }
        for (Spec sp : kind.attrs().values()) {
            if (!out.containsKey(sp.key()) && sp.type() != Type.SECRET && !sp.def().isBlank() && !in.containsKey(sp.key())) out.put(sp.key(), existing.getOrDefault(sp.key(), sp.def()));
            if (sp.type() == Type.SECRET && !in.containsKey(sp.key()) && existing.containsKey(sp.key())) out.put(sp.key(), existing.get(sp.key()));
        }
        if ("LIB_CATEGORY".equals(kindName) && out.containsKey("parent") && !err.containsKey("parent")) {
            long parent = Long.parseLong(out.get("parent"));
            Integer ok = jdbc.queryForObject("select count(*) from catalog_items where id = ? and institution_id = ? and kind = 'LIB_CATEGORY'", Integer.class, parent, inst);
            if (ok == null || ok == 0) err.put("parent", "Choose an existing category.");
            else if (selfId != null && parent == selfId) err.put("parent", "A category cannot be its own parent.");
        }
        if ("CUSTOM_FIELD".equals(kindName) && "DROPDOWN".equals(out.get("dataType")) && !out.containsKey("options")) err.put("options", "Add at least one option (comma separated).");
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        Integer clash = selfId == null
                ? jdbc.queryForObject("select count(*) from catalog_items where institution_id = ? and kind = ? and (lower(name) = lower(?) or (?::text is not null and lower(code) = lower(?::text)))", Integer.class, inst, kindName, name, code, code)
                : jdbc.queryForObject("select count(*) from catalog_items where institution_id = ? and kind = ? and id <> ? and (lower(name) = lower(?) or (?::text is not null and lower(code) = lower(?::text)))", Integer.class, inst, kindName, selfId, name, code, code);
        if (clash != null && clash > 0) throw duplicate(kind);
        return new Prepared(name, code, body.active() == null || body.active(), out);
    }

    private FieldErrorsException duplicate(Kind kind) {
        return new FieldErrorsException(Map.of("name", "A " + kind.label().toLowerCase() + " with this name or code already exists."));
    }

    private long insert(Long inst, String kind, String name, String code, boolean active, int pos, Map<String, String> attrs) {
        Long id = jdbc.queryForObject("insert into catalog_items (institution_id, kind, name, code, active, position) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class, inst, kind, name, code, active, pos);
        attrs.forEach((k, v) -> putAttr(id, k, v));
        return id;
    }

    private void putAttr(Long id, String key, String value) {
        jdbc.update("insert into catalog_item_attrs (item_id, attr_key, attr_value) values (?, ?, ?) on conflict (item_id, attr_key) do update set attr_value = excluded.attr_value", id, key, value);
    }

    private String nextCode(Long inst, String kind, String prefix) {
        Integer max = jdbc.queryForObject("select coalesce(max(cast(substring(code from '[0-9]+$') as integer)), 0) from catalog_items where institution_id = ? and kind = ? and code like ?",
                Integer.class, inst, kind, prefix + "%");
        return String.format("%s%03d", prefix, (max == null ? 0 : max) + 1);
    }

    private String codeOf(Long id) {
        List<String> r = jdbc.queryForList("select code from catalog_items where id = ?", String.class, id);
        return r.isEmpty() ? null : r.get(0);
    }

    private String nameOf(Long inst, String kind, Long id) {
        List<String> r = jdbc.queryForList("select name from catalog_items where id = ? and institution_id = ? and kind = ?", String.class, id, inst, kind);
        if (r.isEmpty()) throw ApiException.notFound("Item");
        return r.get(0);
    }

    private Map<String, String> attrsOf(Long inst, String kind, Long id) {
        nameOf(inst, kind, id);
        Map<String, String> m = new LinkedHashMap<>();
        jdbc.query("select attr_key, attr_value from catalog_item_attrs where item_id = ?", rs -> { m.put(rs.getString(1), rs.getString(2)); }, id);
        return m;
    }

    private List<ItemView> views(Long inst, String kindName, Long onlyId) {
        Kind kind = kind(kindName);
        Map<Long, Map<String, String>> attrs = new LinkedHashMap<>();
        jdbc.query("select a.item_id, a.attr_key, a.attr_value from catalog_item_attrs a join catalog_items i on i.id = a.item_id where i.institution_id = ? and i.kind = ?"
                + (onlyId == null ? "" : " and i.id = " + onlyId.longValue()), rs -> {
            String k = rs.getString(2);
            Spec sp = kind.attrs().get(k);
            String v = sp != null && sp.type() == Type.SECRET ? CommSpec.MASK : rs.getString(3);
            attrs.computeIfAbsent(rs.getLong(1), x -> new LinkedHashMap<>()).put(k, v);
        }, inst, kindName);
        return jdbc.query("select id, name, code, active, position from catalog_items where institution_id = ? and kind = ?" + (onlyId == null ? "" : " and id = " + onlyId.longValue())
                + " order by position, id", (rs, n) -> new ItemView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5),
                attrs.getOrDefault(rs.getLong(1), Map.of())), inst, kindName);
    }

    private Kind kind(String name) {
        Kind k = CatalogSpec.of(name);
        if (k == null) throw ApiException.notFound("List");
        return k;
    }
}
