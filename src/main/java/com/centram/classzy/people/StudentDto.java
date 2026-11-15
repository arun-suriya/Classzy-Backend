package com.centram.classzy.people;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class StudentDto {
    private StudentDto() {
    }

    public record Request(
            @NotBlank @Size(max = 40) String admissionNo,
            @NotBlank @Size(max = 200) String fullName,
            Long schoolClassId,
            Long sectionId,
            boolean active) {
    }

    public record Response(Long id, String admissionNo, String fullName, Long schoolClassId, String className,
                           Long sectionId, String sectionName, boolean active) {
    }
}
