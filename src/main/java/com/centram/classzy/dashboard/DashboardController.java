package com.centram.classzy.dashboard;

import java.util.List;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.auth.UserRepository;
import com.centram.classzy.billing.BillingService;
import com.centram.classzy.people.StudentRepository;
import com.centram.classzy.people.TeacherRepository;
import com.centram.classzy.security.Role;
import com.centram.classzy.security.Roles;
import com.centram.classzy.system.HealthService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Everything the Super Admin dashboard needs besides the institutions list, in one call. */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize(Roles.SUPER)
public class DashboardController {

    /** parents is null because parent accounts don't exist yet. */
    public record Users(long students, long teachers, Long parents, long staff) {
    }

    public record Dashboard(Users users, BillingService.Summary revenue, List<AuditService.Entry> activity,
                            HealthService.Snapshot health) {
    }

    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final UserRepository users;
    private final BillingService billing;
    private final AuditService audit;
    private final HealthService health;

    public DashboardController(StudentRepository students, TeacherRepository teachers, UserRepository users,
                               BillingService billing, AuditService audit, HealthService health) {
        this.students = students;
        this.teachers = teachers;
        this.users = users;
        this.billing = billing;
        this.audit = audit;
        this.health = health;
    }

    @GetMapping("/super-admin")
    public Dashboard superAdmin() {
        long staff = users.countByRole(Role.INSTITUTION_ADMIN);
        return new Dashboard(new Users(students.count(), teachers.count(), null, staff), billing.summary(),
                audit.recent(5), health.check());
    }
}
