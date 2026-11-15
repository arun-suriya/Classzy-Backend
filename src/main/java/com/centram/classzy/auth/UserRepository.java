package com.centram.classzy.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    long countByRole(com.centram.classzy.security.Role role);

    @Modifying
    @Transactional
    @Query(value = "update users set last_login_at = now() where id = ?1", nativeQuery = true)
    void touchLastLogin(Long id);

    /** False when today is outside the account's valid-from / valid-to dates. */
    @Query(value = "select coalesce((valid_from is null or valid_from <= current_date) and (valid_to is null or valid_to >= current_date), true) from users where id = ?1", nativeQuery = true)
    boolean withinValidity(Long id);
}
