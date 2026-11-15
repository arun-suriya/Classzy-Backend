package com.centram.classzy.academic.grading;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class GradingDto {
    private GradingDto() {
    }

    public record SystemRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String description,
            boolean active,
            String gradeType,
            Integer totalMarks,
            Integer classFrom,
            Integer classTo,
            Integer passPercentage) {
    }

    public record GradeRequest(
            @NotBlank @Size(max = 10) String grade,
            @NotNull @Min(0) @Max(100) Integer marksFrom,
            @NotNull @Min(0) @Max(100) Integer marksTo,
            @NotNull @DecimalMin("0") @DecimalMax("99") BigDecimal gradePoint,
            @Size(max = 100) String description) {
    }

    public record GradeResponse(Long id, String grade, Integer marksFrom, Integer marksTo,
                                BigDecimal gradePoint, String description) {
        static GradeResponse of(Grade g) {
            return new GradeResponse(g.getId(), g.getGrade(), g.getMarksFrom(), g.getMarksTo(),
                    g.getGradePoint(), g.getDescription());
        }
    }

    public record SystemResponse(Long id, String name, String description, boolean active, String gradeType, int totalMarks,
                                 Integer classFrom, Integer classTo, int passPercentage, List<GradeResponse> grades) {
        static SystemResponse of(GradingSystem s, List<Grade> grades) {
            return new SystemResponse(s.getId(), s.getName(), s.getDescription(), s.isActive(), s.getGradeType(), s.getTotalMarks(),
                    s.getClassFrom(), s.getClassTo(), s.getPassPercentage(),
                    grades.stream().map(GradeResponse::of).toList());
        }
    }
}
