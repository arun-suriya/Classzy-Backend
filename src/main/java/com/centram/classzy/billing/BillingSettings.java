package com.centram.classzy.billing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.communication.CommSpec;
import com.centram.classzy.communication.CommSpec.Spec;
import com.centram.classzy.communication.CommSpec.Type;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-wide billing preferences (one set for all institutions). */
@Service
@Transactional
public class BillingSettings {

    public static final List<String> METHODS = List.of("BANK_TRANSFER", "UPI", "CHEQUE", "CASH", "CARD", "ONLINE");
    /** Yearly price of each plan in rupees; editable under Billing Settings. */
    public static final Map<String, String> PRICES = new LinkedHashMap<>(Map.of("BASIC", "75000", "STANDARD", "150000", "PREMIUM", "200000", "ENTERPRISE", "500000"));
    private static final String SECTION = "BILLING";
    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    private static void add(Spec s) { SPECS.put(s.key(), s); }

    static {
        add(new Spec("billingCycle", Type.OPT, "YEARLY", false, 0, 0, List.of("MONTHLY", "QUARTERLY", "HALF_YEARLY", "YEARLY")));
        add(new Spec("taxName", Type.TEXT, "GST", true, 0, 20, List.of()));
        add(new Spec("taxRate", Type.DEC, "18", false, 0, 100, List.of()));
        add(new Spec("invoicePrefix", Type.TEXT, "INV", true, 0, 10, List.of()));
        add(new Spec("paymentTermsDays", Type.INT, "15", false, 0, 180, List.of()));
        add(new Spec("autoRenewal", Type.BOOL, "true", false, 0, 0, List.of()));
        add(new Spec("invoiceTemplate", Type.OPT, "DEFAULT", false, 0, 0, List.of("DEFAULT", "COMPACT")));
        for (var e : PRICES.entrySet()) add(new Spec("price" + e.getKey(), Type.DEC, e.getValue(), false, 0, 100000000, List.of()));
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public BillingSettings(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, String> get() {
        Map<String, String> stored = new LinkedHashMap<>();
        jdbc.query("select setting_key, setting_value from platform_settings where section = ?", rs -> { stored.put(rs.getString(1), rs.getString(2)); }, SECTION);
        Map<String, String> out = new LinkedHashMap<>();
        SPECS.forEach((k, sp) -> out.put(k, stored.getOrDefault(k, sp.def())));
        return out;
    }

    /** Yearly price for a plan; PROFESSIONAL (older name) is priced as PREMIUM, TRIAL is free. */
    @Transactional(readOnly = true)
    public java.math.BigDecimal yearlyPrice(String plan) {
        String key = "PROFESSIONAL".equals(plan) ? "PREMIUM" : plan;
        if (key == null || !PRICES.containsKey(key)) return java.math.BigDecimal.ZERO;
        return new java.math.BigDecimal(get().get("price" + key));
    }

    public Map<String, String> save(Map<String, String> body) {
        Map<String, String> err = new LinkedHashMap<>();
        Map<String, String> clean = new LinkedHashMap<>();
        Map<String, String> current = get();
        for (Map.Entry<String, String> e : body.entrySet()) {
            Spec sp = SPECS.get(e.getKey());
            if (sp == null) { err.put(e.getKey(), "Unknown setting."); continue; }
            try {
                String v = CommSpec.clean(sp, e.getValue());
                if (v != null) clean.put(sp.key(), v);
            } catch (IllegalArgumentException ex) { err.put(sp.key(), ex.getMessage()); }
        }
        String prefix = clean.getOrDefault("invoicePrefix", current.get("invoicePrefix"));
        if (!err.containsKey("invoicePrefix") && !prefix.matches("[A-Za-z0-9]{1,10}")) err.put("invoicePrefix", "Use letters and numbers only.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        clean.forEach((k, v) -> jdbc.update("insert into platform_settings (section, setting_key, setting_value) values (?, ?, ?) on conflict (section, setting_key) do update set setting_value = excluded.setting_value", SECTION, k, v));
        audit.record("BILLING_SETTINGS_UPDATED", "BillingSettings", null, null, "Billing settings updated");
        return get();
    }
}
