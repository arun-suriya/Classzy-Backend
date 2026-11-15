package com.centram.classzy.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.institution.Institution;
import com.centram.classzy.institution.InstitutionEnums.PlanType;
import com.centram.classzy.institution.InstitutionRepository;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    public record Input(@NotNull Long institutionId, @NotNull BigDecimal amount, @NotNull LocalDate paidOn,
                        PlanType planType, @Size(max = 20) String method, @Size(max = 100) String reference,
                        @Size(max = 300) String note) {
    }

    public record Row(Long id, Long institutionId, String institutionName, BigDecimal amount, LocalDate paidOn,
                      PlanType planType, String method, String reference, String note) {
    }

    public record Month(String month, String label, BigDecimal revenue, BigDecimal cumulative) {
    }

    public record Summary(BigDecimal annualRevenue, String financialYear, BigDecimal thisMonth, long paymentCount,
                          List<Month> months) {
    }

    private final PaymentRepository payments;
    private final InstitutionRepository institutions;
    private final AuditService audit;

    public BillingService(PaymentRepository payments, InstitutionRepository institutions, AuditService audit) {
        this.payments = payments;
        this.institutions = institutions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Row> list(Long institutionId) {
        Map<Long, String> names = names();
        var list = institutionId == null ? payments.findAllByOrderByPaidOnDescIdDesc()
                : payments.findByInstitutionIdOrderByPaidOnDescIdDesc(institutionId);
        return list.stream().map(p -> toRow(p, names)).toList();
    }

    @Transactional
    public Row record(Input in) {
        Institution inst = institutions.findById(in.institutionId()).orElseThrow(() -> ApiException.notFound("Institution"));
        Map<String, String> errors = new HashMap<>();
        if (in.amount().signum() <= 0) errors.put("amount", "must be greater than zero");
        if (in.amount().scale() > 2) errors.put("amount", "can have at most 2 decimal places");
        if (in.amount().compareTo(new BigDecimal("999999999.99")) > 0) errors.put("amount", "is too large");
        if (in.paidOn().isAfter(LocalDate.now())) errors.put("paidOn", "can't be in the future");
        if (!errors.isEmpty()) throw new FieldErrorsException(errors);

        var p = new Payment();
        p.setInstitutionId(inst.getId());
        p.setAmount(in.amount());
        p.setPaidOn(in.paidOn());
        p.setPlanType(in.planType() != null ? in.planType() : inst.getPlanType());
        p.setMethod(in.method() == null || in.method().isBlank() ? "BANK_TRANSFER" : in.method().trim().toUpperCase(Locale.ROOT));
        p.setReference(blank(in.reference()));
        p.setNote(blank(in.note()));
        p = payments.save(p);
        audit.record("PAYMENT_RECEIVED", "PAYMENT", p.getId(), inst.getId(),
                "₹" + in.amount().stripTrailingZeros().toPlainString() + " received from " + inst.getName());
        return toRow(p, Map.of(inst.getId(), inst.getName()));
    }

    @Transactional
    public void delete(Long id) {
        Payment p = payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment"));
        payments.delete(p);
        audit.record("PAYMENT_DELETED", "PAYMENT", id, p.getInstitutionId(), "A payment of ₹" + p.getAmount().stripTrailingZeros().toPlainString() + " was deleted");
    }

    /** Last 6 months of revenue and the current Indian financial year (April-March) total. */
    @Transactional(readOnly = true)
    public Summary summary() { return summary(6); }

    @Transactional(readOnly = true)
    public Summary summary(int monthCount) {
        int n = monthCount >= 12 ? 12 : 6;
        LocalDate today = LocalDate.now();
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate fyStart = LocalDate.of(fyStartYear, 4, 1);
        YearMonth first = YearMonth.from(today).minusMonths(n - 1);
        LocalDate from = fyStart.isBefore(first.atDay(1)) ? fyStart : first.atDay(1);

        Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
        BigDecimal annual = BigDecimal.ZERO;
        long count = 0;
        for (Payment p : payments.findByPaidOnGreaterThanEqual(from)) {
            byMonth.merge(YearMonth.from(p.getPaidOn()), p.getAmount(), BigDecimal::add);
            if (!p.getPaidOn().isBefore(fyStart)) {
                annual = annual.add(p.getAmount());
                count++;
            }
        }
        List<Month> months = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            YearMonth ym = first.plusMonths(i);
            BigDecimal revenue = byMonth.getOrDefault(ym, BigDecimal.ZERO);
            running = running.add(revenue);
            months.add(new Month(ym.toString(), ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH), revenue, running));
        }
        return new Summary(annual, fyStartYear + "-" + String.valueOf(fyStartYear + 1).substring(2), byMonth.getOrDefault(YearMonth.from(today), BigDecimal.ZERO), count, months);
    }

    private Map<Long, String> names() {
        Map<Long, String> m = new HashMap<>();
        institutions.findAll().forEach(i -> m.put(i.getId(), i.getName()));
        return m;
    }

    private static Row toRow(Payment p, Map<Long, String> names) {
        return new Row(p.getId(), p.getInstitutionId(), names.getOrDefault(p.getInstitutionId(), "—"), p.getAmount(),
                p.getPaidOn(), p.getPlanType(), p.getMethod(), p.getReference(), p.getNote());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
