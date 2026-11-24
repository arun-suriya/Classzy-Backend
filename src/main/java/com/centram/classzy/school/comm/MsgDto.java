package com.centram.classzy.school.comm;

import java.util.List;

public final class MsgDto {
    private MsgDto() {}

    public record Code(String code, String label) {}
    public record ClassOpt(long id, String name) {}
    public record SecOpt(long id, long classId, String name) {}
    public record Options(List<ClassOpt> classes, List<SecOpt> sections, List<Code> audiences, List<Code> channels, List<Code> categories, List<Code> announcementAudiences) {}
    public record Preview(int total, int reachable) {}

    public record SendBody(String audience, Long classId, Long sectionId, String channel, String subject, String body, String scheduleDate, String scheduleTime) {}
    public record MsgRow(long id, String subject, String audience, String channel, String status, String sentAt, String scheduledAt, int recipients, int reachable, String note, String createdBy) {}
    public record MsgPage(List<MsgRow> items, long total, int page, int size) {}
    public record Recipient(String name, String studentName, String contact, boolean reachable) {}
    public record MsgDetail(MsgRow row, String body, List<Recipient> recipients, boolean truncated) {}

    public record TplRow(long id, String name, String category, String channel, String subject, String body) {}
    public record TplBody(String name, String category, String channel, String subject, String body) {}

    public record Notif(long id, String kind, String title, String body, String link, String createdAt, boolean read) {}
    public record NotifPage(List<Notif> items, long total, int page, int size, long unread) {}

    public record AnnRow(long id, String title, String body, String audience, String publishDate, String status, String createdBy) {}
    public record AnnPage(List<AnnRow> items, long total, int page, int size) {}
    public record AnnBody(String title, String body, String audience, String publishDate, String status) {}
    public record StatusBody(String status) {}
}
