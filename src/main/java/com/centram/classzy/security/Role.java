package com.centram.classzy.security;

public enum Role {
    SUPER_ADMIN,
    INSTITUTION_ADMIN,
    TEACHER,
    // accounts that exist but have no portal yet; they cannot sign in
    STUDENT,
    PARENT,
    STAFF
}
