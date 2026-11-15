package com.centram.classzy.academic.subject;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SubjectDto {
    private SubjectDto() {
    }

    public record Request(
            @NotNull Long academicYearId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 20) String code,
            @NotBlank String category,
            @NotEmpty Set<Integer> classLevels,
            boolean active,
            @Size(max = 500) String description) {
    }

    public record Response(Long id, Long academicYearId, String name, String code, String category,
                           List<Integer> classLevels, boolean active, String description) {
        public static Response of(Subject s) {
            return new Response(s.getId(), s.getAcademicYearId(), s.getName(), s.getCode(), s.getCategory(),
                    s.getGradeLevels().stream().sorted().toList(), s.isActive(), s.getDescription());
        }
    }
}
