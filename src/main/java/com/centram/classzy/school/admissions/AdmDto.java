package com.centram.classzy.school.admissions;

import java.util.List;

public final class AdmDto {
    private AdmDto() {}

    public record Year(long id, String name, boolean current) {}
    public record ClassOpt(long id, String name, long yearId) {}
    public record SectionOpt(long id, long classId, String name, Integer max, long enrolled) {}
    public record Code(String code, String label) {}
    public record Options(List<Year> years, List<ClassOpt> classes, List<SectionOpt> sections, List<Code> stages, List<Code> sources, List<Code> docTypes) {}
    public record Counts(long applications, long enquiries, long waitlist, long offers, long admitted) {}

    public record AppRow(long id, String applicationNo, String studentName, String grade, Long classId, Long yearId, String stage, String submittedAt, String guardianName, String phone, long docs, long docsVerified) {}
    public record AppPage(List<AppRow> items, long total, int page, int size) {}
    public record DocRow(long id, String docType, String fileName, long size, boolean verified, String uploadedAt) {}
    public record OfferRow(long id, String offerNo, long applicationId, String applicationNo, String applicant, String grade, String expiresOn, String status, String sentAt, String respondedAt, String note, Long classId) {}
    public record AppDetail(AppRow row, String dob, String guardianEmail, String waitlistedAt, Long studentId, Long enquiryId, List<DocRow> documents, OfferRow offer) {}

    public record EnqRow(long id, String enquiryNo, String name, String phone, String email, String childName, String grade, String source, String status, String followUpOn, String note, Long applicationId, String createdAt) {}
    public record EnqPage(List<EnqRow> items, long total, int page, int size) {}

    public record WaitRow(int rank, long id, String applicationNo, String studentName, String grade, String waitlistedOn) {}
    public record Waitlist(Integer seats, List<WaitRow> items) {}
    public record OfferPage(List<OfferRow> items, long total, int page, int size) {}
    public record AdmittedRow(long applicationId, String admissionNo, Long studentId, String student, String grade, String section, String enrolledOn) {}
    public record AdmittedPage(List<AdmittedRow> items, long total, int page, int size) {}

    public record AppBody(String studentName, String dob, Long academicYearId, Long classId, String guardianName, String phone, String email, Boolean submit, Long enquiryId) {}
    public record EnqBody(String name, String phone, String email, String childName, String grade, String source, String followUpOn, String note) {}
    public record StageBody(String stage) {}
    public record OfferBody(Long applicationId, String expiresOn, String note) {}
    public record AdmitBody(Long sectionId) {}
    public record VerifyBody(Boolean verified) {}
}
