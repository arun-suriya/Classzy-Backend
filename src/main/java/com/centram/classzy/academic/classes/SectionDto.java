package com.centram.classzy.academic.classes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SectionDto {
    private SectionDto() {
    }

    public record Request(
            @NotNull Long schoolClassId,
            @NotBlank @Size(max = 40) String name,
            Long teacherId,
            @Size(max = 60) String room,
            @Min(1) Integer maxStudents,
            boolean active,
            @Size(max = 500) String description) {
    }

    public record Response(Long id, Long schoolClassId, String className, String name, Long teacherId,
                           String teacherName, String room, Integer maxStudents, boolean active,
                           String description, long studentCount) {
    }
}
