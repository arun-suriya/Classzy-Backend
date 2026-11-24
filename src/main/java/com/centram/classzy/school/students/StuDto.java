package com.centram.classzy.school.students;

import java.util.List;

public final class StuDto {
    private StuDto() {}

    public record Code(String code, String label) {}
    public record Options(List<Code> relationships, List<Code> docTypes) {}
    public record Row(long id, String admissionNo, String name, String grade, Long classId, Long yearId, String section, Long sectionId, boolean active) {}
    public record Page(List<Row> items, long total, int page, int size, long active, long inactive) {}
    public record Doc(long id, String docType, String fileName, long size, String uploadedAt) {}
    public record Detail(Row row, String yearName, String dob, String joiningDate, String guardianName, String relationship, String phone, String email, String address,
                         String inactiveReason, Long applicationId, String createdAt, List<Doc> documents) {}
    public record Body(String fullName, String dob, Long academicYearId, Long classId, Long sectionId, String joiningDate, String guardianName, String relationship, String phone, String email, String address) {}
    public record StatusBody(Boolean active, String reason) {}
}
