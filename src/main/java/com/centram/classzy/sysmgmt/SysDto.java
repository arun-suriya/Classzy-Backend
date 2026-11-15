package com.centram.classzy.sysmgmt;

import java.util.List;
import java.util.Map;

public final class SysDto {
    private SysDto() {}

    public record Card(String status, String label, String detail) {}

    public record BackupRow(Long id, String type, String trigger, String status, long sizeBytes, int tables, long rows, String note, String createdBy, String createdAt) {}

    public record Header(Card health, long instances, long instancesNew, long storageUsed, int capacityGb, double storagePercent, Card security, List<String> issues, BackupRow lastBackup) {}

    public record InstanceRow(Long id, String name, Long institutionId, String institution, String kind, String environment, String version, String status, String lastRefresh, String notes) {}

    public record InstanceBody(String name, Long institutionId, String kind, String environment, String version, String status, String notes) {}

    public record IntegrationRow(Long id, String name, String type, String source, String target, String endpoint, String status, String lastRun, Integer lastMs, String message) {}

    public record IntegrationBody(String name, String type, String source, String target, String endpoint) {}

    public record DataRow(String code, String name, long total, String lastUpdated, String status) {}

    public record SecurityRow(String key, String label, boolean enabled, String summary, String updatedAt, Map<String, String> values) {}

    public record ServiceRow(String name, String status, Double uptime, Integer responseMs, String lastChecked, String detail) {}

    public record Overview(Header header, List<InstanceRow> instances, List<IntegrationRow> integrations, List<DataRow> data, List<SecurityRow> security, List<ServiceRow> monitoring,
                           List<BackupRow> backups, Map<String, String> config) {}

    public record SettingsBody(Map<String, String> values) {}

    public record BackupBody(String type) {}
}
