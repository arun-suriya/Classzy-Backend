package com.centram.classzy.communication;

import java.time.LocalDateTime;

public final class CommDto {
    private CommDto() {}

    public record TestResult(boolean ok, String message) {}

    public record Variable(String name, String description) {}

    public record SenderBody(String senderId, String senderType, String status, boolean defaultSender) {}

    public record SenderView(Long id, String senderId, String senderType, String status, boolean defaultSender) {
        static SenderView of(SmsSenderId s) { return new SenderView(s.getId(), s.senderId, s.senderType, s.status, s.defaultSender); }
    }

    public record TemplateBody(String channel, String name, String category, String title, String body, boolean active) {}

    public record TemplateView(Long id, String channel, String name, String category, String title, String body, boolean active, LocalDateTime updatedAt) {
        static TemplateView of(CommTemplate t) { return new TemplateView(t.getId(), t.channel, t.name, t.category, t.title, t.body, t.active, t.updatedAt); }
    }

    public record PushCategoryBody(String name, String description, boolean active, boolean mobile, boolean web) {}

    public record PushCategoryView(Long id, String name, String description, boolean active, boolean mobile, boolean web) {
        static PushCategoryView of(PushCategory c) { return new PushCategoryView(c.getId(), c.name, c.description, c.active, c.mobile, c.web); }
    }
}
