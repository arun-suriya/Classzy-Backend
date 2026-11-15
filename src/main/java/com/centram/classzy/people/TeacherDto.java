package com.centram.classzy.people;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TeacherDto {
    private TeacherDto() {
    }

    public record Request(
            @NotBlank @Size(max = 200) String fullName,
            @Email @Size(max = 200) String email,
            @Size(max = 30) String phone,
            boolean active) {
    }

    public record Response(Long id, String fullName, String email, String phone, boolean active) {
        static Response of(Teacher t) {
            return new Response(t.getId(), t.getFullName(), t.getEmail(), t.getPhone(), t.isActive());
        }
    }
}
