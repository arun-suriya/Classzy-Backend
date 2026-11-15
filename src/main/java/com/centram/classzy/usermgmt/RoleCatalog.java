package com.centram.classzy.usermgmt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.security.Role;

/** Fixed list of roles a user can hold, with the modules each one is meant to cover. Informational: sign-in access follows the account type. */
public final class RoleCatalog {
    private RoleCatalog() {}

    public record Perm(String module, String level) {}
    public record RoleView(String code, String name, String description, List<Perm> permissions) {}

    public static final List<String> MODULES = List.of("Dashboard", "Academic", "Attendance", "Exams & Grading", "Timetable", "Library", "Transport", "Fees & Payments", "Communications", "Reports");
    public static final List<String> USER_TYPES = List.of("STUDENT", "TEACHER", "PARENT", "STAFF", "ADMINISTRATOR");

    private static final Map<String, RoleView> ROLES = new LinkedHashMap<>();

    /** spec: "Dashboard:V,Academic:M" where V = view, M = manage. */
    private static void add(String code, String name, String description, String spec) {
        List<Perm> perms = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] kv = part.split(":");
            perms.add(new Perm(kv[0], "M".equals(kv[1]) ? "MANAGE" : "VIEW"));
        }
        ROLES.put(code, new RoleView(code, name, description, perms));
    }

    static {
        add("ADMINISTRATOR", "Administrator", "Manage institution settings and users", "Dashboard:M,Academic:M,Attendance:M,Exams & Grading:M,Timetable:M,Library:M,Transport:M,Fees & Payments:M,Communications:M,Reports:M");
        add("PRINCIPAL", "Principal", "Academic and operational access", "Dashboard:V,Academic:M,Attendance:V,Exams & Grading:V,Timetable:V,Fees & Payments:V,Communications:M,Reports:V");
        add("TEACHER", "Teacher", "Class and student related access", "Dashboard:V,Academic:V,Attendance:M,Exams & Grading:M,Timetable:V,Communications:V");
        add("CLASS_TEACHER", "Class Teacher", "Manage their own class", "Dashboard:V,Academic:V,Attendance:M,Exams & Grading:M,Timetable:V,Communications:M,Reports:V");
        add("SUBJECT_TEACHER", "Subject Teacher", "Teach and grade their subjects", "Dashboard:V,Academic:V,Attendance:M,Exams & Grading:M,Timetable:V");
        add("DEPARTMENT_HEAD", "Department Head", "Oversee a department", "Dashboard:V,Academic:M,Attendance:V,Exams & Grading:V,Timetable:V,Reports:V");
        add("EXAM_COORDINATOR", "Exam Coordinator", "Plan and run examinations", "Dashboard:V,Academic:V,Exams & Grading:M,Timetable:V,Reports:V");
        add("STUDENT", "Student", "Limited access for students", "Dashboard:V,Attendance:V,Exams & Grading:V,Timetable:V,Library:V");
        add("PARENT", "Parent", "Access to own child/children", "Dashboard:V,Attendance:V,Exams & Grading:V,Fees & Payments:V,Communications:V,Transport:V");
        add("LIBRARIAN", "Librarian", "Library management access", "Dashboard:V,Library:M,Communications:V");
        add("TRANSPORT_STAFF", "Transport Staff", "Transport management access", "Dashboard:V,Transport:M,Communications:V");
        add("ACCOUNTANT", "Accountant", "Fees and finance access", "Dashboard:V,Fees & Payments:M,Reports:V");
    }

    public static List<RoleView> all() { return List.copyOf(ROLES.values()); }
    public static RoleView get(String code) { return ROLES.get(code); }

    /** The sign-in role an account of this type gets. */
    public static Role loginRole(String userType) {
        return switch (userType) {
            case "ADMINISTRATOR" -> Role.INSTITUTION_ADMIN;
            case "TEACHER" -> Role.TEACHER;
            case "STUDENT" -> Role.STUDENT;
            case "PARENT" -> Role.PARENT;
            default -> Role.STAFF;
        };
    }
}
