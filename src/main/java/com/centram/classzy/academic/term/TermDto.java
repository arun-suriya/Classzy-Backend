package com.centram.classzy.academic.term;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TermDto {
    private TermDto() {
    }

    public record Request(
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 60) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal weightage,
            boolean active,
            @Size(max = 500) String description) {
    }

    public record Response(Long id, Long academicYearId, String name, LocalDate startDate, LocalDate endDate,
                           BigDecimal weightage, boolean active, String description) {
        public static Response of(Term t) {
            return new Response(t.getId(), t.getAcademicYearId(), t.getName(), t.getStartDate(), t.getEndDate(),
                    t.getWeightage(), t.isActive(), t.getDescription());
        }
    }
}
