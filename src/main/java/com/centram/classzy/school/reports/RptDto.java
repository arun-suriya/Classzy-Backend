package com.centram.classzy.school.reports;

import java.util.List;

public final class RptDto {
    private RptDto() {}
    public record Code(String code, String label) {}
    public record Year(Long id, String name, boolean current) {}
    public record ClassOpt(Long id, Long yearId, String name) {}
    public record SecOpt(Long id, Long classId, String name) {}
    public record TermOpt(Long id, Long yearId, String name) {}
    public record SubjectOpt(Long id, Long yearId, String name) {}
    /** One report a card can generate. available=false means the data for it doesn't exist yet (reason says why). */
    public record Report(String code, String tab, String group, String label, boolean available, String reason) {}
    public record SourceCol(String key, String label, boolean aggregate) {}
    public record Source(String code, String label, List<SourceCol> columns) {}
    public record Options(List<Year> years, List<ClassOpt> classes, List<SecOpt> sections, List<TermOpt> terms, List<SubjectOpt> subjects, List<Report> reports, List<Source> sources) {}
    public record RunBody(String report, Long yearId, Long classId, Long sectionId, Long termId, Long subjectId) {}
    public record CustomBody(String name, String source, List<String> columns, Long yearId, Long classId, Long sectionId) {}
    public record Table(String title, String subtitle, List<String> columns, List<Boolean> numeric, List<List<String>> rows, List<String> total, boolean truncated, String note) {}
    public record Saved(Long id, String name, String source, List<String> columns, Long yearId, Long classId, Long sectionId, String createdBy) {}
}
