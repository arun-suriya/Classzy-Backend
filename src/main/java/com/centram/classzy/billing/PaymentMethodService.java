package com.centram.classzy.billing;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.billing.BillingOverviewDto.MethodBody;
import com.centram.classzy.billing.BillingOverviewDto.MethodRow;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saved cards shown under Payment Methods. Only brand, last 4 digits and expiry are stored. */
@Service
@Transactional
public class PaymentMethodService {

    private static final Set<String> BRANDS = Set.of("VISA", "MASTERCARD", "AMEX", "RUPAY", "OTHER");

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PaymentMethodService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MethodRow> list() {
        YearMonth now = YearMonth.now();
        return jdbc.query("select id, brand, last4, expiry_month, expiry_year, is_default from payment_methods order by is_default desc, id",
                (rs, n) -> new MethodRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getBoolean(6), YearMonth.of(rs.getInt(5), rs.getInt(4)).isBefore(now)));
    }

    public MethodRow create(MethodBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String brand = b.brand() == null ? "" : b.brand().trim().toUpperCase();
        if (!BRANDS.contains(brand)) err.put("brand", "Choose a card brand.");
        String last4 = b.last4() == null ? "" : b.last4().trim();
        if (!last4.matches("\\d{4}")) err.put("last4", "Enter the last 4 digits only.");
        Integer m = b.expiryMonth(), y = b.expiryYear();
        if (m == null || m < 1 || m > 12) err.put("expiryMonth", "Enter a month from 1 to 12.");
        if (y == null) err.put("expiryYear", "Enter the expiry year.");
        else { if (y < 100) y += 2000; if (y > 2100) err.put("expiryYear", "Enter a valid year."); }
        if (err.isEmpty() && YearMonth.of(y, m).isBefore(YearMonth.now())) err.put("expiryYear", "This card has already expired.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        if (jdbc.queryForObject("select count(*) from payment_methods where brand = ? and last4 = ? and expiry_month = ? and expiry_year = ?", Integer.class, brand, last4, m, y) > 0)
            throw new FieldErrorsException(Map.of("last4", "This card is already saved."));
        boolean first = jdbc.queryForObject("select count(*) from payment_methods", Integer.class) == 0;
        boolean def = first || Boolean.TRUE.equals(b.makeDefault());
        if (def) jdbc.update("update payment_methods set is_default = false");
        Long id = jdbc.queryForObject("insert into payment_methods (brand, last4, expiry_month, expiry_year, is_default) values (?, ?, ?, ?, ?) returning id", Long.class, brand, last4, m, y, def);
        audit.record("PAYMENT_METHOD_ADDED", "PAYMENT_METHOD", id, null, brand + " card ending " + last4 + " added");
        return list().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    public List<MethodRow> setDefault(Long id) {
        if (jdbc.queryForObject("select count(*) from payment_methods where id = ?", Integer.class, id) == 0) throw ApiException.notFound("Payment method");
        jdbc.update("update payment_methods set is_default = false where is_default");
        jdbc.update("update payment_methods set is_default = true where id = ?", id);
        audit.record("PAYMENT_METHOD_DEFAULT", "PAYMENT_METHOD", id, null, "Default payment method changed");
        return list();
    }

    public void delete(Long id) {
        List<Boolean> was = jdbc.queryForList("select is_default from payment_methods where id = ?", Boolean.class, id);
        if (was.isEmpty()) throw ApiException.notFound("Payment method");
        jdbc.update("delete from payment_methods where id = ?", id);
        if (was.get(0)) jdbc.update("update payment_methods set is_default = true where id = (select min(id) from payment_methods)");
        audit.record("PAYMENT_METHOD_REMOVED", "PAYMENT_METHOD", id, null, "A saved payment method was removed");
    }
}
