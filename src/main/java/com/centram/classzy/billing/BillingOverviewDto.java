package com.centram.classzy.billing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class BillingOverviewDto {
    private BillingOverviewDto() {}

    public record Stats(long institutions, long newThisMonth, BigDecimal annualRevenue, String financialYear, Double growthPct, long paymentsThisYear,
                        long active, long trial, long expiring, long expired, long subscribed) {}

    public record Count(String key, long count) {}

    public record SubRow(Long institutionId, String institutionName, String plan, String startDate, String endDate, String status, String cycle, BigDecimal amount) {}

    public record RenewalRow(Long institutionId, String institutionName, String plan, String renewalDate, long daysLeft, BigDecimal amount) {}

    public record InvoiceRow(Long id, String invoiceNo, Long institutionId, String institutionName, String issueDate, String dueDate, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal amount,
                             String description, String status, String paidOn) {}

    public record MethodRow(Long id, String brand, String last4, int expiryMonth, int expiryYear, boolean isDefault, boolean expired) {}

    public record MethodBody(String brand, String last4, Integer expiryMonth, Integer expiryYear, Boolean makeDefault) {}

    public record InvoiceDetail(InvoiceRow invoice, String billTo, String institutionEmail, String taxName, String taxRate, String template, String paymentTerms) {}

    public record Overview(Stats stats, BillingService.Summary revenue, List<Count> statuses, List<Count> plans, List<SubRow> subscriptions, List<RenewalRow> renewals,
                           List<InvoiceRow> invoices, List<MethodRow> methods, Map<String, String> settings) {}

    public record InvoiceBody(Long institutionId, BigDecimal subtotal, String issueDate, String dueDate, String description) {}

    public record PayBody(String paidOn, String method, String reference) {}
}
