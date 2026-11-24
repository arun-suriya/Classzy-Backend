package com.centram.classzy.school.calendar;

import java.util.List;

public final class CalDto {
    private CalDto() {}
    public record Code(String code, String label) {}
    public record ClassOpt(Long id, String name) {}
    public record SecOpt(Long id, Long classId, String name) {}
    public record Options(List<Code> types, List<Code> audiences, List<Code> reminders, List<ClassOpt> classes, List<SecOpt> sections) {}
    public record Event(Long id, String title, String type, boolean allDay, String startDate, String startTime, String endDate, String endTime, String audience, Long classId, Long sectionId, String audienceLabel, String location, String description, String reminder, String createdBy) {}
    public record Body(String title, String type, Boolean allDay, String startDate, String startTime, String endDate, String endTime, String audience, Long classId, Long sectionId, String location, String description, String reminder) {}
}
