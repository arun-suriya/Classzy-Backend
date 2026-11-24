package com.centram.classzy.school.settings;

import java.util.List;

public final class SetDto {
    private SetDto() {}
    public record Code(String code, String label) {}
    public record YearOpt(Long id, String name, boolean current) {}
    public record General(String name, Long academicYearId, String language, String timeZone, String dateFormat, boolean hasLogo) {}
    public record Academic(String gradingScale, int periodDuration, boolean attendancePerPeriod, boolean lateMarkingAllowed, int lateMarkingMinutes, String termsSummary, String gradesSummary) {}
    public record Admissions(Long yearId, String opens, String closes, List<String> documents, String workflow) {}
    public record Fees(String currency, String billingFrequency, String dueDateRule, String lateFeeRule, double lateFeeAmount, String receiptPrefix) {}
    public record Integration(String service, String label, String provider, String status, List<String> providers) {}
    public record Options(List<YearOpt> years, List<String> languages, List<String> timeZones, List<String> dateFormats, List<Code> gradingScales, List<String> documents, List<Code> workflows,
                          List<String> currencies, List<Code> billing, List<Code> dueRules, List<Code> lateRules, List<Code> roles) {}
    public record All(General general, Academic academic, Admissions admissions, Fees fees, List<Integration> integrations, Options options) {}

    public record GeneralBody(String name, Long academicYearId, String language, String timeZone, String dateFormat) {}
    public record AcademicBody(String gradingScale, Integer periodDuration, Boolean attendancePerPeriod, Boolean lateMarkingAllowed, Integer lateMarkingMinutes) {}
    public record AdmissionsBody(Long yearId, String opens, String closes, List<String> documents, String workflow) {}
    public record FeesBody(String currency, String billingFrequency, String dueDateRule, String lateFeeRule, Double lateFeeAmount, String receiptPrefix) {}
    public record IntegrationBody(String provider) {}

    public record SchoolUser(Long id, String fullName, String email, String userType, String role, String status, boolean canSignIn, boolean self) {}
    public record InviteBody(String firstName, String lastName, String email, String phone, String role) {}
    public record RoleBody(String role) {}
    public record StatusBody(String status) {}
    public record Invited(SchoolUser user, String temporaryPassword, boolean canSignIn) {}
}
