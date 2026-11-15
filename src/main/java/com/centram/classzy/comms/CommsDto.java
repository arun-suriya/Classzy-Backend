package com.centram.classzy.comms;

import java.util.List;

public final class CommsDto {
    private CommsDto() {}

    public record Row(Long id, String title, String description, String type, String priority, String audienceKind, List<String> targets, String audienceLabel,
                      String subject, String body, String status, String scheduledAt, String sentAt, Integer recipients, String createdAt) {}

    public record Page(List<Row> items, long total, int page, int size) {}

    public record Summary(long total, long createdThisMonth, long sent, long drafts, long scheduled, long audienceReached) {}

    public record Option(String code, String label) {}

    public record Options(List<Option> groups, List<Option> institutions, List<Option> sections) {}

    /** action: DRAFT, SEND or SCHEDULE. scheduledAt is an ISO-8601 instant. */
    public record Body(String title, String description, String type, String priority, String audienceKind, List<String> targets,
                       String subject, String body, String action, String scheduledAt) {}

    public record ResolveBody(String audienceKind, List<String> targets) {}

    public record Resolved(long count, String label) {}
}
