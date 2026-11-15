package com.centram.classzy.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.centram.classzy.auth.UserRepository;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.institution.InstitutionRepository;
import com.centram.classzy.people.StudentRepository;
import com.centram.classzy.people.TeacherRepository;
import com.centram.classzy.security.Role;
import com.centram.classzy.security.Roles;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only list of people across every institution (the "View All" of the User Overview card). */
@RestController
@RequestMapping("/api/platform/users")
@PreAuthorize(Roles.SUPER)
public class PlatformUsersController {

    public record UserRow(Long id, String type, String name, String email, String detail, Long institutionId,
                          String institutionName, boolean active) {
    }

    public record PageOf(List<UserRow> items, long total, int page, int size) {
    }

    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final UserRepository users;
    private final InstitutionRepository institutions;

    public PlatformUsersController(StudentRepository students, TeacherRepository teachers, UserRepository users,
                                   InstitutionRepository institutions) {
        this.students = students;
        this.teachers = teachers;
        this.users = users;
        this.institutions = institutions;
    }

    /** type = STUDENT, TEACHER or STAFF (institution admins). */
    @GetMapping
    @Transactional(readOnly = true)
    public PageOf list(@RequestParam(defaultValue = "STUDENT") String type,
                       @RequestParam(required = false) String search,
                       @RequestParam(required = false) Long institutionId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size) {
        Map<Long, String> names = new HashMap<>();
        institutions.findAll().forEach(i -> names.put(i.getId(), i.getName()));

        List<UserRow> rows = new ArrayList<>();
        switch (type.toUpperCase(Locale.ROOT)) {
            case "STUDENT" -> students.findAll().forEach(s -> rows.add(new UserRow(s.getId(), "STUDENT", s.getFullName(), null,
                    "Admission no. " + s.getAdmissionNo(), s.getInstitutionId(), names.get(s.getInstitutionId()), s.isActive())));
            case "TEACHER" -> teachers.findAll().forEach(t -> rows.add(new UserRow(t.getId(), "TEACHER", t.getFullName(), t.getEmail(),
                    t.getPhone(), t.getInstitutionId(), names.get(t.getInstitutionId()), t.isActive())));
            case "STAFF" -> users.findAll().stream().filter(u -> u.getRole() == Role.INSTITUTION_ADMIN)
                    .forEach(u -> rows.add(new UserRow(u.getId(), "STAFF", u.getFullName(), u.getEmail(), "Institution Admin",
                            u.getInstitutionId(), names.get(u.getInstitutionId()), u.isActive())));
            default -> throw ApiException.badRequest("type must be STUDENT, TEACHER or STAFF.");
        }

        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<UserRow> filtered = rows.stream()
                .filter(r -> institutionId == null || institutionId.equals(r.institutionId()))
                .filter(r -> q.isEmpty() || Stream.of(r.name(), r.email(), r.detail(), r.institutionName())
                        .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(q)))
                .sorted((a, b) -> String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name())))
                .toList();

        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(page, 0);
        int from = Math.min(p * s, filtered.size());
        return new PageOf(filtered.subList(from, Math.min(from + s, filtered.size())), filtered.size(), p, s);
    }
}
