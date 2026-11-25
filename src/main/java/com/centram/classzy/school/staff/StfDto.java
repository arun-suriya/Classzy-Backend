package com.centram.classzy.school.staff;

import java.util.List;

public final class StfDto {
    private StfDto() {}
    public record Code(String code, String label) {}
    public record Desig(String staffType, String name) {}
    public record DeptOpt(Long id, String name) {}
    public record StaffOpt(Long id, String code, String name, Long departmentId, boolean active) {}
    public record Options(List<DeptOpt> departments, List<Desig> designations, List<Code> staffTypes, List<Code> leaveTypes, List<StaffOpt> staff, String today) {}

    public record StaffRow(Long id, String code, String name, String email, String phone, Long departmentId, String department, String designation, String staffType, String typeLabel, String joiningDate, boolean active, boolean onLeave) {}
    public record StaffPage(List<StaffRow> items, long total) {}
    public record StaffBody(String fullName, String email, String phone, Long departmentId, String designation, String staffType, String joiningDate) {}

    public record LeaveRow(Long id, Long teacherId, String staff, String code, String type, String typeLabel, String from, String to, int days, String reason, String status, String decidedBy) {}
    public record LeaveBody(Long teacherId, String type, String from, String to, String reason) {}
    public record Overview(long total, long teaching, long nonTeaching, long onLeave, List<LeaveRow> upcoming) {}

    public record Dept(Long id, String name, Long headId, String head, long staff) {}
    public record DeptBody(String name, Long headId) {}

    public record Cycle(Long id, String name, String dueDate, long total, long completed) {}
    public record CycleBody(String name, String dueDate, Long departmentId) {}
    public record Appraisal(Long id, Long cycleId, Long teacherId, String staff, String department, Long reviewerId, String reviewer, String dueDate, String status, Double rating, String comments) {}
    public record AppraisalBody(Long reviewerId, String dueDate, String status, Double rating, String comments) {}

    public record Training(Long id, String title, String date, String time, String venue, String trainer, String description, String status, int participants, List<Long> teacherIds) {}
    public record TrainingBody(String title, String date, String time, String venue, String trainer, String description, List<Long> teacherIds) {}

    public record Vacancy(Long id, String position, Long departmentId, String department, int openings, String description, String closingDate, String status, long applicants, long hired) {}
    public record VacancyBody(String position, Long departmentId, Integer openings, String description, String closingDate) {}
    public record Applicant(Long id, Long vacancyId, String name, String email, String phone, String status, String notes, String appliedAt) {}
    public record ApplicantBody(String name, String email, String phone, String notes, String status) {}
}
