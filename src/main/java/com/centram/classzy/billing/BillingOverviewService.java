package com.centram.classzy.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.billing.BillingOverviewDto.Count;
import com.centram.classzy.billing.BillingOverviewDto.Overview;
import com.centram.classzy.billing.BillingOverviewDto.RenewalRow;
import com.centram.classzy.billing.BillingOverviewDto.Stats;
import com.centram.classzy.billing.BillingOverviewDto.SubRow;
import com.centram.classzy.institution.Institution;
import com.centram.classzy.institution.InstitutionEnums.Status;
import com.centram.classzy.institution.InstitutionRepository;
import com.centram.classzy.institution.InstitutionService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything the Subscription & Billing overview page shows, in one call. */
@Service
@Transactional(readOnly = true)
public class BillingOverviewService {

    private final InstitutionRepository institutions;
    private final JdbcTemplate jdbc;
    private final BillingService billing;
    private final BillingSettings settings;
    private final InvoiceService invoices;
    private final PaymentMethodService methods;

    public BillingOverviewService(InstitutionRepository institutions, JdbcTemplate jdbc, BillingService billing, BillingSettings settings, InvoiceService invoices, PaymentMethodService methods) {
        this.methods = methods;
        this.institutions = institutions;
        this.jdbc = jdbc;
        this.billing = billing;
        this.settings = settings;
        this.invoices = invoices;
    }

    public Overview overview(int months) {
        LocalDate today = LocalDate.now();
        List<Institution> all = institutions.findAll().stream().filter(i -> InstitutionService.statusOf(i) != Status.ARCHIVED).toList();
        Map<Institution, Status> status = new LinkedHashMap<>();
        all.forEach(i -> status.put(i, InstitutionService.statusOf(i)));
        List<Institution> subscribed = all.stream().filter(i -> status.get(i) != Status.DRAFT).toList();

        long active = count(status, Status.ACTIVE), trial = count(status, Status.TRIAL), expiring = count(status, Status.EXPIRING_SOON), expired = count(status, Status.EXPIRED);
        long newThisMonth = all.stream().filter(i -> i.getCreatedAt() != null && !i.getCreatedAt().isBefore(today.withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())).count();

        BillingService.Summary revenue = billing.summary(months);
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate fyStart = LocalDate.of(fyStartYear, 4, 1);
        LocalDate prevStart = fyStart.minusYears(1);
        LocalDate prevSameDay = prevStart.plusDays(ChronoUnit.DAYS.between(fyStart, today));
        BigDecimal prev = jdbc.queryForObject("select coalesce(sum(amount), 0) from payments where paid_on >= ? and paid_on <= ?", BigDecimal.class, prevStart, prevSameDay);
        Double growth = prev.signum() > 0 ? revenue.annualRevenue().subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 1, java.math.RoundingMode.HALF_UP).doubleValue() : null;
        Stats stats = new Stats(all.size(), newThisMonth, revenue.annualRevenue(), revenue.financialYear(), growth, revenue.paymentCount(), active, trial, expiring, expired, subscribed.size());

        List<Count> statuses = List.of(new Count("ACTIVE", active), new Count("TRIAL", trial), new Count("EXPIRING_SOON", expiring), new Count("EXPIRED", expired));
        Map<String, Long> byPlan = new LinkedHashMap<>();
        for (String p : List.of("BASIC", "STANDARD", "PREMIUM", "ENTERPRISE")) byPlan.put(p, 0L);
        subscribed.stream().filter(i -> i.getPlanType() != null).forEach(i -> byPlan.merge(planKey(i), 1L, Long::sum));
        if (byPlan.getOrDefault("TRIAL", 0L) == 0) byPlan.remove("TRIAL");
        List<Count> plans = byPlan.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList();

        List<SubRow> subs = new ArrayList<>();
        subscribed.stream().filter(i -> i.getPlanType() != null)
                .sorted(Comparator.comparing(Institution::getSubscriptionStart, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Institution::getId, Comparator.reverseOrder()))
                .limit(5).forEach(i -> subs.add(new SubRow(i.getId(), i.getName(), planKey(i), str(i.getSubscriptionStart()), str(i.getSubscriptionEnd()), status.get(i).name(),
                        cycle(i.getSubscriptionStart(), i.getSubscriptionEnd()), status.get(i) == Status.TRIAL ? BigDecimal.ZERO : fee(i))));

        List<RenewalRow> renewals = new ArrayList<>();
        subscribed.stream().filter(i -> i.getSubscriptionEnd() != null && !i.getSubscriptionEnd().isBefore(today))
                .sorted(Comparator.comparing(Institution::getSubscriptionEnd)).limit(5)
                .forEach(i -> renewals.add(new RenewalRow(i.getId(), i.getName(), i.getPlanType() == null ? null : planKey(i), i.getSubscriptionEnd().toString(),
                        ChronoUnit.DAYS.between(today, i.getSubscriptionEnd()), fee(i))));

        return new Overview(stats, revenue, statuses, plans, subs, renewals, invoices.list(null, 5), methods.list(), settings.get());
    }

    private static long count(Map<Institution, Status> m, Status s) { return m.values().stream().filter(v -> v == s).count(); }
    private static String str(LocalDate d) { return d == null ? null : d.toString(); }

    /** Length of the subscription period, described as a billing cycle. */
    static String cycle(LocalDate start, LocalDate end) {
        if (start == null || end == null) return "—";
        long months = ChronoUnit.MONTHS.between(start, end.plusDays(1));
        return switch ((int) months) {
            case 1 -> "Monthly";
            case 3 -> "Quarterly";
            case 6 -> "Half-yearly";
            case 12 -> "Yearly";
            default -> months > 0 ? months + " months" : "—";
        };
    }

    /** PROFESSIONAL is the older name of PREMIUM. */
    private static String planKey(Institution i) {
        String p = i.getPlanType().name();
        return "PROFESSIONAL".equals(p) ? "PREMIUM" : p;
    }

    /** Subscription fee: the plan's yearly price, scaled to the length of the subscription period. */
    private BigDecimal fee(Institution i) {
        if (i.getPlanType() == null) return BigDecimal.ZERO;
        BigDecimal yearly = settings.yearlyPrice(planKey(i));
        long months = i.getSubscriptionStart() != null && i.getSubscriptionEnd() != null ? ChronoUnit.MONTHS.between(i.getSubscriptionStart(), i.getSubscriptionEnd().plusDays(1)) : 12;
        if (months <= 0) months = 12;
        return yearly.multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
    }
}
