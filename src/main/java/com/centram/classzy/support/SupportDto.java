package com.centram.classzy.support;

import java.util.List;

public final class SupportDto {
    private SupportDto() {}

    public record Ticket(Long id, String ticketNo, String subject, String category, String priority, String status, Long institutionId, String institution, String requester,
                         Long assignedTo, String assignedName, boolean escalated, String createdBy, String createdAt, String updatedAt) {}

    public record Detail(Ticket ticket, String description, int messages, int attachments) {}

    public record Page(List<Ticket> items, long total, int page, int size) {}

    public record Metric(long value, Double change) {}

    public record Summary(Metric total, Metric open, Metric inProgress, Metric awaiting, Metric resolved) {}

    public record Option(String code, String label) {}

    public record Options(List<Option> categories, List<Option> agents, List<Option> institutions) {}

    public record CreateBody(String subject, String description, String category, String priority, Long institutionId, String requester, Long assigneeId) {}

    public record UpdateBody(String status, String priority, String category, Long assigneeId) {}

    public record MessageBody(String body, Boolean internal) {}

    public record Message(Long id, String author, String body, boolean internal, String createdAt) {}

    public record Event(String actor, String text, String createdAt) {}

    public record Attachment(Long id, String fileName, String contentType, long size, String uploadedBy, String createdAt) {}

    public record Article(Long id, String title, String category, String body, boolean published, String createdBy, String updatedAt) {}

    public record ArticleBody(String title, String category, String body, Boolean published) {}
}
