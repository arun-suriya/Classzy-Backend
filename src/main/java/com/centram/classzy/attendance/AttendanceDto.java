package com.centram.classzy.attendance;

import java.util.List;

/** Request/response shapes for the Attendance Settings API. */
public final class AttendanceDto {
    private AttendanceDto() {
    }

    /** General tab + Rules & Policies tab values. Working days / hours come from the institution profile. */
    public record Settings(
            String attendanceMode, String defaultMethod, String defaultMark,
            int lateAfterMinutes, int halfDayMinutes,
            boolean biometric, boolean rfid, boolean mobileApp, boolean gps, boolean offlineMode, boolean autoSync,
            boolean parentView, boolean studentView, boolean remarksEnabled, boolean adjustmentEnabled,
            int minAttendancePct, boolean countPresent, boolean countLate, boolean countHalfDay,
            boolean countOnLeave, boolean countSickLeave, boolean countExcused,
            boolean excludeHolidays, boolean excludeWeekOffs, int gracePeriodMinutes, String autoAbsentTime,
            List<String> workingDays, String startTime, String endTime) {
    }

    public record TypeBody(String name, String code, String category, String countsAs, String color,
                           boolean defaultType, boolean active, String description) {
    }

    public record TypeView(Long id, String name, String code, String category, String countsAs, String color,
                           boolean defaultType, boolean active, String description) {
        static TypeView of(AttendanceType t) {
            return new TypeView(t.getId(), t.name, t.code, t.category, t.countsAs, t.color, t.defaultType, t.active, t.description);
        }
    }

    public record RuleBody(String name, String description, String appliesTo, int priority, boolean active,
                           String conditionField, String conditionOperator, String conditionValue,
                           String actionTypeCode, boolean notifyParent, boolean includeInReports) {
    }

    public record RuleView(Long id, String name, String description, String appliesTo, int priority, boolean active,
                           String conditionField, String conditionOperator, String conditionValue,
                           String actionTypeCode, boolean notifyParent, boolean includeInReports) {
        static RuleView of(AttendanceRule r) {
            return new RuleView(r.getId(), r.name, r.description, r.appliesTo, r.priority, r.active,
                    r.conditionField, r.conditionOperator, r.conditionValue, r.actionTypeCode, r.notifyParent, r.includeInReports);
        }
    }

    public record TemplateBody(String name, String body, boolean active) {
    }

    public record TemplateView(Long id, String name, String body, boolean active) {
        static TemplateView of(AttendanceTemplate t) {
            return new TemplateView(t.getId(), t.name, t.body, t.active);
        }
    }

    public record EventBody(String name, String triggerCode, List<String> recipients, List<String> channels,
                            Long templateId, String customMessage, boolean active) {
    }

    public record EventView(Long id, String name, String triggerCode, List<String> recipients, List<String> channels,
                            Long templateId, String customMessage, boolean active) {
        static EventView of(AttendanceNotificationEvent e) {
            return new EventView(e.getId(), e.name, e.triggerCode, split(e.recipients), split(e.channels),
                    e.templateId, e.customMessage, e.active);
        }

        private static List<String> split(String csv) {
            return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
        }
    }
}
