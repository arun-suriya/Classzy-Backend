package com.centram.classzy.school.fees;

import java.util.List;

import com.centram.classzy.school.admissions.AdmDto.ClassOpt;
import com.centram.classzy.school.admissions.AdmDto.Year;

public final class FeeDto {
    private FeeDto() {}

    public record Code(String code, String label) {}
    public record TermOpt(long id, long yearId, String name, String start, String end) {}
    public record Options(List<Year> years, List<TermOpt> terms, List<ClassOpt> classes, List<Code> frequencies, List<Code> modes) {}
    public record StudentHit(long id, String admissionNo, String name, String grade, String section) {}

    public record InvoiceRow(long id, String invoiceNo, long studentId, String student, String admissionNo, String grade, String feeType, String term, double amount, double net, double paid, double balance, String dueDate, String status) {}
    public record InvoicePage(List<InvoiceRow> items, long total, int page, int size, double billed, double collected, double outstanding) {}
    public record PayRow(long id, String receiptNo, double amount, String paidOn, String mode, String reference, String notes, double refundable) {}
    public record InvoiceDetail(InvoiceRow row, double concession, double scholarship, List<PayRow> payments) {}
    public record StudentPay(long paymentId, String receiptNo, String invoiceNo, String feeType, String paidOn, double amount, double refundable) {}
    public record PayBody(Long invoiceId, Double amount, String paidOn, String mode, String reference, String notes) {}
    public record Receipt(long paymentId, String receiptNo, String school, String paidOn, double amount, String mode, String reference, String notes, String invoiceNo, String feeType, String student, String admissionNo, String grade, String section, double invoiceNet, double balance, String receivedBy) {}

    public record StructRow(long id, long yearId, long classId, String grade, String feeHead, String frequency, double amount, boolean active, long invoices) {}
    public record StructBody(Long yearId, Long classId, String feeHead, String frequency, Double amount, Boolean active) {}
    public record GenBody(Long yearId, Long classId, Long termId, String dueDate) {}
    public record GenResult(int created, int existing, int termFeesSkipped, int students) {}

    public record ConcRow(long id, String student, String admissionNo, String grade, String invoiceNo, String feeHead, Double percent, double amount, String reason, String status, String requestedAt) {}
    public record ConcPage(List<ConcRow> items, long total, int page, int size) {}
    public record ConcBody(Long invoiceId, Double percent, String reason) {}

    public record ScholarRow(long id, long studentId, String student, String admissionNo, String name, double award, double applied, String validity, String status, String year) {}
    public record ScholarPage(List<ScholarRow> items, long total, int page, int size) {}
    public record ScholarBody(Long studentId, Long yearId, Long termId, String name, Double amount, String validity) {}

    public record RefundRow(long id, String refundNo, String student, String admissionNo, String receiptNo, double amount, String reason, String status, String requestedAt, String decidedAt) {}
    public record RefundPage(List<RefundRow> items, long total, int page, int size) {}
    public record RefundBody(Long paymentId, Double amount, String reason) {}
    public record DecisionBody(Boolean approve) {}
}
