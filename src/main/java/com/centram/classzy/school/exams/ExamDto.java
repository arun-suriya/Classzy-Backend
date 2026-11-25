package com.centram.classzy.school.exams;

import java.util.List;

public final class ExamDto {
    private ExamDto() {}
    public record Code(String code, String label) {}
    public record YearOpt(Long id, String name, boolean current) {}
    public record TermOpt(Long id, Long yearId, String name) {}
    public record ClassOpt(Long id, Long yearId, String name) {}
    public record SectionOpt(Long id, Long classId, String className, String name) {}
    public record ClassSubject(Long classId, Long subjectId, String name) {}
    public record Options(List<YearOpt> years, List<TermOpt> terms, List<ClassOpt> classes, List<SectionOpt> sections, List<ClassSubject> classSubjects, List<Code> types, List<Code> scales) {}

    public record ExamRow(Long id, String name, Long yearId, Long termId, String termName, String type, String typeLabel, String startDate, String endDate, String scale, double passPercent,
                          boolean published, String stage, List<Long> sectionIds, String gradesLabel, int papers, String createdBy) {}
    public record ExamBody(String name, Long yearId, Long termId, String type, List<Long> sectionIds, String startDate, String endDate, String scale, Double passPercent) {}
    public record Upcoming(Long examId, String examName, String subject, String grade, String date, String stage) {}
    public record Overview(long scheduled, long inProgress, long pendingReview, List<Upcoming> upcoming, List<ExamRow> exams) {}

    public record Paper(Long id, Long examId, Long classId, String className, Long subjectId, String subjectName, String date, String startTime, String endTime, String room, double maxMarks) {}
    public record PaperBody(String date, String startTime, String endTime, String room, Double maxMarks) {}
    public record GenBody(String startTime, Integer minutes) {}
    public record GenResult(int created, int existing) {}

    public record Band(String grade, Integer min, Integer max, String remark) {}
    public record BandsBody(String scale, List<Band> bands) {}

    public record EntryRow(Long studentId, String admissionNo, String name, Double marks, boolean absent) {}
    public record Entry(String status, double maxMarks, double passPercent, boolean editable, String reviewedBy, List<EntryRow> rows) {}
    public record EntryRowBody(Long studentId, Double marks, Boolean absent) {}
    public record EntryBody(Long subjectId, Long sectionId, List<EntryRowBody> rows) {}
    public record Ref(Long subjectId, Long sectionId) {}

    public record Table(String title, String subtitle, List<String> columns, List<Boolean> numeric, List<List<String>> rows, List<String> total, String note) {}
}
