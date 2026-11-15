package com.centram.classzy.exam;

import java.util.List;

/** Request/response shapes for the Exam & Grading Settings API. */
public final class ExamDto {
    private ExamDto() {
    }

    public record TypeBody(Long academicYearId, String name, String code, String description, Integer classFrom, Integer classTo,
                           int weightage, String category, boolean active) {
    }

    public record TypeView(Long id, Long academicYearId, String name, String code, String description, Integer classFrom,
                           Integer classTo, int weightage, String category, boolean active) {
        static TypeView of(ExamType t) {
            return new TypeView(t.getId(), t.academicYearId, t.name, t.code, t.description, t.classFrom, t.classTo, t.weightage, t.category, t.active);
        }
    }

    public record ReportBody(Long academicYearId, String name, String reportType, Integer classFrom, Integer classTo,
                             List<String> sections, boolean active) {
    }

    public record ReportView(Long id, Long academicYearId, String name, String reportType, Integer classFrom, Integer classTo,
                             List<String> sections, boolean active) {
        static ReportView of(ReportTemplate r) {
            return new ReportView(r.getId(), r.academicYearId, r.name, r.reportType, r.classFrom, r.classTo,
                    r.sections == null || r.sections.isBlank() ? List.of() : List.of(r.sections.split(",")), r.active);
        }
    }

    public record ResultBody(Long academicYearId, String name, String calculationType, Integer classFrom, Integer classTo,
                             List<Long> examTypeIds, String roundingRule, String displayFormat, boolean active) {
    }

    public record ResultView(Long id, Long academicYearId, String name, String calculationType, Integer classFrom, Integer classTo,
                             List<Long> examTypeIds, String roundingRule, String displayFormat, boolean active) {
        static ResultView of(ResultTemplate r) {
            return new ResultView(r.getId(), r.academicYearId, r.name, r.calculationType, r.classFrom, r.classTo,
                    r.examTypeIds.stream().sorted().toList(), r.roundingRule, r.displayFormat, r.active);
        }
    }

    public record Settings(
            String publicationMethod, boolean showSubjectRank, boolean allowReevaluation, Long defaultGradingId,
            int maxMarksPerSubject, boolean considerAttendance,
            boolean teachersEnterMarks, boolean reviewApproval, boolean editBeforePublish, boolean notifyOnPublish,
            boolean reevaluationProcess, List<String> approvalLevels, String workflowRemarks,
            boolean showGradeAndMarks, boolean parentAccess, boolean showTeacherComments, boolean autoGeneratePdf,
            boolean showAttendanceSummary, String pdfTemplate, boolean includeSchoolLogo) {
    }
}
