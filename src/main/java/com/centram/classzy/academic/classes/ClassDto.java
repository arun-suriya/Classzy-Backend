package com.centram.classzy.academic.classes;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ClassDto {
    private ClassDto() {
    }

    public record Request(
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 60) String name,
            @Min(1) @Max(12) Integer gradeLevel,
            boolean active,
            @Size(max = 500) String description) {
    }

    public record Response(Long id, Long academicYearId, String name, Integer gradeLevel, boolean active,
                           String description, long studentCount, long sectionCount) {
    }
}
