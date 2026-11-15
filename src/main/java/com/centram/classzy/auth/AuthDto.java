package com.centram.classzy.auth;

import jakarta.validation.constraints.NotBlank;

public final class AuthDto {
    private AuthDto() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserInfo(Long id, String email, String fullName, String role, Long institutionId) {
        static UserInfo of(User u) {
            return new UserInfo(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.getInstitutionId());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserInfo user) {
    }
}
