package com.centram.classzy.usermgmt;

import java.util.List;
import java.util.Map;

public final class UserMgmtDto {
    private UserMgmtDto() {}

    /** Dates are ISO strings (yyyy-MM-dd). */
    public record UserBody(
            String userType, String firstName, String lastName, String email, String phoneCode, String phone, String dateOfBirth, String gender,
            String status, String validFrom, String validTo, String notes,
            String passwordMode, String password, Boolean mustChangePassword, Boolean twoFactor, Integer resetAfterDays,
            List<String> roles, Long institutionId, String dataScope, Long academicYearId,
            String employeeId, String department, String designation, String joinedOn, String altEmail, String emergencyContact,
            String address1, String address2, String city, String state, String pinCode,
            List<String> notifyEvents, String language, String timezone, List<String> notifyVia) {}

    public record UserRow(Long id, String fullName, String email, String userType, List<String> roles, Long institutionId, String institutionName, String status, String lastLogin) {}

    public record UserPage(List<UserRow> items, long total, int page, int size) {}

    public record UserDetail(
            Long id, String userCode, String userType, String firstName, String lastName, String fullName, String email, String phoneCode, String phone, String dateOfBirth, String gender,
            String status, String validFrom, String validTo, String notes, Boolean mustChangePassword, Boolean twoFactor, Integer resetAfterDays,
            List<String> roles, Long institutionId, String institutionName, String institutionCode, String institutionType, String dataScope, Long academicYearId, String academicYearName,
            String employeeId, String department, String designation, String joinedOn, String altEmail, String emergencyContact,
            String address1, String address2, String city, String state, String pinCode,
            List<String> notifyEvents, String language, String timezone, List<String> notifyVia,
            String createdAt, String lastLogin) {}

    public record Created(UserDetail user, String temporaryPassword) {}

    public record Summary(long total, Map<String, Long> byType, Map<String, Long> newThisMonth, long active, long inactive, long suspended) {}

    public record StatusBody(String status) {}

    public record ResetBody(String password) {}

    public record ResetResult(String temporaryPassword) {}

    public record YearOption(Long id, String name) {}

    public record ActivityItem(String at, String actor, String action, String description) {}
}
