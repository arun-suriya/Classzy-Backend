package com.centram.classzy.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.billing.BillingOverviewDto.InvoiceBody;
import com.centram.classzy.billing.BillingOverviewDto.InvoiceRow;
import com.centram.classzy.billing.BillingOverviewDto.PayBody;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvoiceService {

    static final String SELECT = "select v.id, v.invoice_no, v.institution_id, i.name, v.issue_date, v.due_date, v.subtotal, v.tax_amount, v.amount, v.description, "
            + "case when v.payment_id is not null then 'PAID' when v.due_date < current_date then 'OVERDUE' else 'PENDING' end, case when v.payment_id is not null then v.paid_on end "
            + "from invoices v join institutions i on i.id = v.institution_id";

    private final JdbcTemplate jdbc;
    private final BillingSettings settings;
    private final BillingService payments;
    private final AuditService audit;

    public InvoiceService(JdbcTemplate jdbc, BillingSettings settings, BillingService payments, AuditService audit) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.payments = payments;
        this.audit = audit;
    }

    static InvoiceRow map(ResultSet rs) throws SQLException {
        return new InvoiceRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getDate(5).toString(), rs.getDate(6).toString(), rs.getBigDecimal(7), rs.getBigDecimal(8),
                rs.getBigDecimal(9), rs.getString(10), rs.getString(11), rs.getDate(12) == null ? null : rs.getDate(12).toString());
    }

    @Transactional(readOnly = true)
    public List<InvoiceRow> list(String status, int limit) {
        int n = Math.min(Math.max(limit, 1), 200);
        String where = switch (status == null ? "" : status.toUpperCase()) {
            case "PAID" -> " where v.payment_id is not null";
            case "OVERDUE" -> " where v.payment_id is null and v.due_date < current_date";
            case "PENDING" -> " where v.payment_id is null and v.due_date >= current_date";
            default -> "";
        };
        return jdbc.query(SELECT + where + " order by v.issue_date desc, v.id desc limit " + n, (rs, i) -> map(rs));
    }

    public InvoiceRow create(InvoiceBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        Map<String, String> cfg = settings.get();
        if (b.institutionId() == null) err.put("institutionId", "Select an institution.");
        else if (jdbc.queryForObject("select count(*) from institutions where id = ? and lifecycle <> 'ARCHIVED'", Integer.class, b.institutionId()) == 0) err.put("institutionId", "Choose an active institution.");
        BigDecimal sub = b.subtotal();
        if (sub == null || sub.signum() <= 0) err.put("subtotal", "Enter an amount greater than zero.");
        else if (sub.scale() > 2) err.put("subtotal", "Use at most 2 decimal places.");
        else if (sub.compareTo(new BigDecimal("99999999.99")) > 0) err.put("subtotal", "Amount is too large.");
        LocalDate issue = date(b.issueDate(), LocalDate.now(), "issueDate", err);
        LocalDate due = date(b.dueDate(), issue == null ? null : issue.plusDays(Integer.parseInt(cfg.get("paymentTermsDays"))), "dueDate", err);
        if (issue != null && due != null && due.isBefore(issue)) err.put("dueDate", "Must be on or after the issue date.");
        String desc = b.description() == null ? null : b.description().trim();
        if (desc != null && desc.length() > 300) err.put("description", "Use 300 characters or fewer.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        BigDecimal tax = sub.multiply(new BigDecimal(cfg.get("taxRate"))).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        String prefix = cfg.get("invoicePrefix").toUpperCase() + "-" + issue.getYear() + "-";
        Integer seq = jdbc.queryForObject("select coalesce(max(cast(substring(invoice_no from '[0-9]+$') as integer)), 0) + 1 from invoices where invoice_no like ?", Integer.class, prefix + "%");
        String no = String.format("%s%03d", prefix, seq);
        Long id;
        try {
            id = jdbc.queryForObject("insert into invoices (institution_id, invoice_no, issue_date, due_date, subtotal, tax_amount, amount, description) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
                    Long.class, b.institutionId(), no, issue, due, sub, tax, sub.add(tax), desc == null || desc.isEmpty() ? null : desc);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Another invoice was created at the same time. Please try again.");
        }
        audit.record("INVOICE_CREATED", "INVOICE", id, b.institutionId(), "Invoice " + no + " created");
        return one(id);
    }

    @Transactional(readOnly = true)
    public BillingOverviewDto.InvoiceDetail detail(Long id) {
        InvoiceRow r = one(id);
        Map<String, String> cfg = settings.get();
        String address = jdbc.queryForObject("select concat_ws(', ', nullif(address, ''), nullif(city, ''), nullif(state, ''), nullif(pin_code, '')) from institutions where id = ?", String.class, r.institutionId());
        List<String> email = jdbc.queryForList("select email from institutions where id = ?", String.class, r.institutionId());
        return new BillingOverviewDto.InvoiceDetail(r, address == null ? "" : address, email.isEmpty() || email.get(0) == null ? "" : email.get(0), cfg.get("taxName"), cfg.get("taxRate"), cfg.get("invoiceTemplate"), cfg.get("paymentTermsDays"));
    }

    public InvoiceRow markPaid(Long id, PayBody b) {
        InvoiceRow inv = one(id);
        if ("PAID".equals(inv.status())) throw ApiException.conflict("This invoice is already paid.");
        Map<String, String> err = new LinkedHashMap<>();
        LocalDate paidOn = date(b == null ? null : b.paidOn(), LocalDate.now(), "paidOn", err);
        if (paidOn != null && paidOn.isAfter(LocalDate.now())) err.put("paidOn", "Can't be in the future.");
        String method = b == null || b.method() == null || b.method().isBlank() ? "BANK_TRANSFER" : b.method().trim().toUpperCase();
        if (!BillingSettings.METHODS.contains(method)) err.put("method", "Choose a valid payment method.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        var row = payments.record(new BillingService.Input(inv.institutionId(), inv.amount(), paidOn, null, method, b == null ? null : b.reference(), "Invoice " + inv.invoiceNo()));
        jdbc.update("update invoices set paid_on = ?, payment_id = ? where id = ?", paidOn, row.id(), id);
        audit.record("INVOICE_PAID", "INVOICE", id, inv.institutionId(), "Invoice " + inv.invoiceNo() + " marked paid");
        return one(id);
    }

    public void delete(Long id) {
        InvoiceRow inv = one(id);
        if ("PAID".equals(inv.status())) throw ApiException.conflict("A paid invoice can't be deleted. Delete the payment first if it was recorded by mistake.");
        jdbc.update("delete from invoices where id = ?", id);
        audit.record("INVOICE_DELETED", "INVOICE", id, inv.institutionId(), "Invoice " + inv.invoiceNo() + " deleted");
    }

    private InvoiceRow one(Long id) {
        List<InvoiceRow> r = jdbc.query(SELECT + " where v.id = ?", (rs, i) -> map(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Invoice");
        return r.get(0);
    }

    private static LocalDate date(String v, LocalDate fallback, String key, Map<String, String> err) {
        if (v == null || v.isBlank()) return fallback;
        try { return LocalDate.parse(v.trim()); } catch (java.time.format.DateTimeParseException e) { err.put(key, "Enter a valid date."); return null; }
    }
}
