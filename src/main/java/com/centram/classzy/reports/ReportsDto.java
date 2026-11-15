package com.centram.classzy.reports;

import java.math.BigDecimal;
import java.util.List;

public final class ReportsDto {
    private ReportsDto() {}

    /** change is the percentage versus last month, or null when there is nothing to compare with. */
    public record Metric(BigDecimal value, Double change) {}

    public record Stats(Metric institutions, Metric users, Metric students, Metric attendance, Metric revenue) {}

    public record TrendPoint(String month, long fresh, long total) {}

    public record Slice(String name, long value, double percent) {}

    public record TopInstitution(Long id, String name, long activeUsers) {}

    public record Overview(Stats stats, List<TrendPoint> enrollment, List<Slice> modules, long moduleTotal, List<TopInstitution> top) {}

    public record Template(String code, String name, String category, String description, String icon, boolean available, String note) {}

    public record GeneratedRow(Long id, String name, String template, String category, String format, String status, int rowCount, String institution, String generatedBy, String generatedAt) {}

    public record Content(String name, String format, String csv) {}

    public record ScheduleRow(Long id, String name, String template, String category, String format, String frequency, int rangeDays, Long institutionId, String institution, String recipients, String status, String nextRun, String lastRun) {}

    public record ScheduleBody(String name, String template, String format, String frequency, Integer days, Long institutionId, String recipients) {}

    /** schedule is optional: when present a recurring schedule is created too. */
    public record GenerateBody(String template, String format, Integer days, Long institutionId, ScheduleBody schedule) {}

    public record Generated(GeneratedRow report, String csv, ScheduleRow schedule) {}
}
