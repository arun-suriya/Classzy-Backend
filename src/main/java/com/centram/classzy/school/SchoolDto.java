package com.centram.classzy.school;

import java.util.List;

public final class SchoolDto {
    private SchoolDto() {}

    public record Metric(double value, Integer changePct, String compare) {}
    public record Pick(long id, String name, boolean current) {}
    public record Month(String label, Integer students) {}
    public record Stage(String code, String label, long count) {}
    public record Fees(double collected, double pending, double total, int percent) {}
    public record Event(long id, String time, String title, String location, String category) {}
    public record PendingItem(String key, String label, long count) {}
    public record Activity(String at, String text, String action) {}
    public record Dashboard(long schoolId, String schoolName, boolean hasLogo, String userName, List<Pick> years, List<Pick> terms, Long yearId, Long termId, String scopeLabel,
                            Metric students, Metric staff, Metric admissions, Metric collected, Metric pending, Metric enquiries,
                            List<Month> enrollment, List<Stage> stages, long applicationsTotal, Fees fees, List<Event> schedule, List<PendingItem> pendingActions, long pendingTotal, List<Activity> activities) {}

    public record LeaveRow(long id, String teacher, String leaveType, String from, String to, String reason, String status) {}
    public record ConcessionRow(long id, String student, String admissionNo, double amount, String reason, String status) {}

    public record EventBody(String title, String date, String time, String location, String category) {}
    public record LeaveBody(Long teacherId, String leaveType, String from, String to, String reason) {}
    public record DecisionBody(Boolean approve) {}
}
