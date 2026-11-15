package com.centram.classzy.security;

/** Expressions for @PreAuthorize. */
public final class Roles {
    private Roles() {
    }

    public static final String READ = "hasAnyRole('SUPER_ADMIN','INSTITUTION_ADMIN','TEACHER')";
    public static final String WRITE = "hasAnyRole('SUPER_ADMIN','INSTITUTION_ADMIN')";
    public static final String SUPER = "hasRole('SUPER_ADMIN')";
}
