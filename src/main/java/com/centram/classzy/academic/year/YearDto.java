package com.centram.classzy.academic.year;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class YearDto {
    private YearDto() {
    }

    public record Request(
            @NotBlank @Size(max = 40) String name,
            @Size(max = 40) String shortName,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            boolean current,
            boolean active,
            @Size(max = 500) String description) {
    }

    public record Response(Long id, String name, String shortName, LocalDate startDate, LocalDate endDate,
                           boolean current, boolean active, String description) {
        public static Response of(AcademicYear y) {
            return new Response(y.getId(), y.getName(), y.getShortName(), y.getStartDate(), y.getEndDate(),
                    y.isCurrentYear(), y.isActive(), y.getDescription());
        }
    }
}
