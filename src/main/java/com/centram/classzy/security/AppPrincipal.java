package com.centram.classzy.security;

/** The signed-in user, rebuilt from the JWT on every request. institutionId is null for Super Admin. */
public record AppPrincipal(Long userId, String email, Role role, Long institutionId) {
}
