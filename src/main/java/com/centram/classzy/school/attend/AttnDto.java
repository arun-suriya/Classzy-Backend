package com.centram.classzy.school.attend;

import java.util.List;

public final class AttnDto {
    private AttnDto() {}
    public record Code(String code, String label) {}
    public record ClassOpt(Long id, String name) {}
    public record SectionOpt(Long id, Long classId, String className, String name) {}
    public record TermOpt(Long id, String name, String startDate, String endDate) {}
    public record Options(String today, String yearName, List<ClassOpt> classes, List<SectionOpt> sections, List<TermOpt> terms, int minPercent, boolean countLate, boolean countLeave,
                          boolean adjustmentEnabled, List<Code> statuses, List<Code> leaveTypes) {}
    public record StudentOpt(Long id, String admissionNo, String name, String grade) {}

    public record DayInfo(String date, boolean schoolDay, String reason) {}
    public record Recent(String student, String grade, String status) {}
    public record Overview(DayInfo day, long total, long present, long absent, long onLeave, long late, long notMarked, double presentPercent, double absentPercent, double leavePercent, List<Recent> recent) {}

    public record DailyRow(Long studentId, String admissionNo, String name, String status, String remarks, boolean fromLeave) {}
    public record Daily(DayInfo day, boolean editable, String lockReason, String sectionName, List<DailyRow> rows) {}
    public record MarkBody(Long studentId, String status, String remarks) {}
    public record DailyBody(String date, Long sectionId, List<MarkBody> rows) {}

    public record Table(String title, String subtitle, List<String> columns, List<Boolean> numeric, List<List<String>> rows, List<String> total, String note) {}

    public record LeaveRow(Long id, Long studentId, String student, String admissionNo, String grade, String type, String typeLabel, String from, String to, String reason, String status, String decidedBy) {}
    public record LeaveBody(Long studentId, String type, String from, String to, String reason) {}

    public record MonthRow(String month, double rate, long below) {}
    public record Trends(double average, String lowestMonth, double lowest, long chronic, int minPercent, List<MonthRow> months) {}

    public record CalDay(String date, String status, String off) {}
    public record Calendar(String student, String grade, String month, List<CalDay> days, long present, long absent, long late, long leave) {}
}
