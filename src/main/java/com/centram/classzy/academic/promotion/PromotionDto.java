package com.centram.classzy.academic.promotion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PromotionDto {
    private PromotionDto() {
    }

    public record RuleRequest(
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 60) String fromClass,
            @NotBlank @Size(max = 60) String toClass,
            @NotNull CriteriaType criteriaType,
            @Min(0) @Max(100) Integer minValue,
            @NotNull SubjectScope subjectScope,
            @Size(max = 300) String criteria,
            @Size(max = 500) String additionalConditions,
            boolean active) {
    }

    public record RuleResponse(Long id, Long academicYearId, String name, String fromClass, String toClass,
                               CriteriaType criteriaType, Integer minValue, SubjectScope subjectScope,
                               String criteria, String additionalConditions, boolean active) {
        public static RuleResponse of(PromotionRule r) {
            return new RuleResponse(r.getId(), r.getAcademicYearId(), r.getName(), r.getFromClass(), r.getToClass(),
                    r.getCriteriaType(), r.getMinValue(), r.getSubjectScope(), r.getCriteria(),
                    r.getAdditionalConditions(), r.isActive());
        }
    }

    public record PreviewRow(Long studentId, String admissionNo, String studentName, String currentClass,
                             String nextClass, ProgressionStatus status, String remarks) {
    }

    public record OverrideRequest(
            @NotNull Long academicYearId,
            @NotNull Long studentId,
            @NotNull ProgressionStatus status,
            @Size(max = 60) String nextClass,
            @Size(max = 300) String remarks) {
    }
}
