package com.centram.classzy.config;

import com.centram.classzy.auth.User;
import com.centram.classzy.auth.UserRepository;
import com.centram.classzy.institution.Institution;
import com.centram.classzy.institution.InstitutionRepository;
import com.centram.classzy.security.Role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * First start only: creates one institution plus a Super Admin and an Institution Admin so you can sign in.
 * No teachers, students or academic data are created - you add those through the API / UI.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final InstitutionRepository institutions;
    private final PasswordEncoder encoder;

    @Value("${classzy.seed.institution-name}") private String institutionName;
    @Value("${classzy.seed.super-admin-email}") private String superEmail;
    @Value("${classzy.seed.super-admin-password}") private String superPassword;
    @Value("${classzy.seed.institution-admin-email}") private String adminEmail;
    @Value("${classzy.seed.institution-admin-password}") private String adminPassword;

    public DataInitializer(UserRepository users, InstitutionRepository institutions, PasswordEncoder encoder) {
        this.users = users;
        this.institutions = institutions;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        Institution inst = institutions.findAllByOrderByNameAsc().stream().findFirst().orElseGet(() -> {
            var i = new Institution();
            i.setName(institutionName);
            i.getWorkingDays().addAll(java.util.List.of(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
                    java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
            i.getModules().addAll(java.util.List.of(com.centram.classzy.institution.InstitutionEnums.ModuleCode.STUDENT_INFORMATION,
                    com.centram.classzy.institution.InstitutionEnums.ModuleCode.ACADEMICS,
                    com.centram.classzy.institution.InstitutionEnums.ModuleCode.ATTENDANCE));
            return institutions.save(i);
        });

        users.save(newUser(superEmail, superPassword, "Super Admin", Role.SUPER_ADMIN, null));
        users.save(newUser(adminEmail, adminPassword, "Institution Admin", Role.INSTITUTION_ADMIN, inst.getId()));
        log.info("Seeded institution '{}' (id {}), Super Admin {} and Institution Admin {}. Change these passwords!",
                inst.getName(), inst.getId(), superEmail, adminEmail);
    }

    private User newUser(String email, String password, String name, Role role, Long institutionId) {
        var u = new User();
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setFullName(name);
        u.setRole(role);
        u.setInstitutionId(institutionId);
        return u;
    }
}
