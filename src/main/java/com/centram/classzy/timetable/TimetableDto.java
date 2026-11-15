package com.centram.classzy.timetable;

import java.util.List;

/** Request/response shapes for the Timetable Settings API. Times are "HH:mm", dates "yyyy-MM-dd". */
public final class TimetableDto {
    private TimetableDto() {
    }

    // ---------------- period configuration
    public record General(
            String configName, Long academicYearId, String description, String timeFormat, String periodNaming, int defaultDuration,
            boolean allowVariableDuration, boolean allowZeroPeriod, boolean allowExtraPeriod, boolean enableBreaks,
            boolean enableLunchBreak, boolean showPeriodNumber, boolean halfDayEnabled, String halfDayEndTime,
            List<String> workingDays, String startTime, String endTime) {
    }

    public record SlotBody(String name, String slotType, String startTime, String endTime, boolean active) {
    }

    public record SlotView(Long id, String name, String slotType, String startTime, String endTime, int durationMinutes, boolean active) {
        static SlotView of(TimetableSlot s) {
            return new SlotView(s.getId(), s.name, s.slotType, hhmm(s.startTime), hhmm(s.endTime),
                    (int) java.time.Duration.between(s.startTime, s.endTime).toMinutes(), s.active);
        }
    }

    public record HolidayBody(String name, String holidayType, String startDate, String endDate, boolean active) {
    }

    public record HolidayView(Long id, String name, String holidayType, String startDate, String endDate, boolean active) {
        static HolidayView of(TimetableHoliday h) {
            return new HolidayView(h.getId(), h.name, h.holidayType, h.startDate.toString(), h.endDate.toString(), h.active);
        }
    }

    // ---------------- rules
    public record Rules(
            int maxPeriodsPerDay, int maxConsecutiveTeacher, int minBreakBetween, int maxConsecutiveSubject,
            boolean allowDoublePeriods, boolean allowFreePeriods, boolean preferMorningCore,
            boolean genAvoidTeacherConflicts, boolean genAvoidRoomConflicts, boolean genDistributeEvenly, boolean genWeeklyFrequency,
            boolean genWorkingHours, boolean genPreferredSlots, boolean genManualAdjust, boolean genAvoidConsecutive,
            boolean genDailyDiversity, boolean genTeacherWorkload, boolean genAiSuggestions, String rulesDescription,
            String onTeacherConflict, String onRoomConflict, String onSubjectConflict, String onLimitExceeded,
            List<String> conflictOrder, boolean advAutoResolve, boolean advReport, boolean advHighlight, boolean advManualOverride) {
    }

    public record PriorityBody(String code, boolean enabled) {
    }

    public record PriorityView(Long id, String code, String name, int position, boolean enabled) {
        static PriorityView of(RulePriority p) {
            return new PriorityView(p.getId(), p.code, p.name, p.position, p.enabled);
        }
    }

    // ---------------- breaks
    public record BreakTypeBody(String name, String code, int defaultDuration, String color, List<String> applicableFor, boolean active, String description) {
    }

    public record BreakTypeView(Long id, String name, String code, int defaultDuration, String color, List<String> applicableFor, boolean active, String description) {
        static BreakTypeView of(BreakType t) {
            return new BreakTypeView(t.getId(), t.name, t.code, t.defaultDuration, t.color,
                    t.applicableFor == null || t.applicableFor.isBlank() ? List.of() : List.of(t.applicableFor.split(",")), t.active, t.description);
        }
    }

    public record BreakRuleBody(String name, Long breakTypeId, String appliesTo, Integer classFrom, Integer classTo, String startTime,
                                int durationMinutes, String frequency, List<String> days, boolean active, String description) {
    }

    public record BreakRuleView(Long id, String name, Long breakTypeId, String breakTypeName, String appliesTo, Integer classFrom, Integer classTo,
                                String startTime, int durationMinutes, String frequency, List<String> days, boolean active, String description) {
        static BreakRuleView of(BreakRule r, String typeName) {
            return new BreakRuleView(r.getId(), r.name, r.breakTypeId, typeName, r.appliesTo, r.classFrom, r.classTo, hhmm(r.startTime),
                    r.durationMinutes, r.frequency, r.days == null || r.days.isBlank() ? List.of() : List.of(r.days.split(",")), r.active, r.description);
        }
    }

    static String hhmm(java.time.LocalTime t) {
        return t == null ? null : t.toString().substring(0, 5);
    }
}
