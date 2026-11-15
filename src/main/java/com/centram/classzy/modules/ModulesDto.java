package com.centram.classzy.modules;

import java.util.List;
import java.util.Map;

public final class ModulesDto {
    private ModulesDto() {}

    public record ModuleRow(String code, String name, String category, String description, String icon, boolean enabled, int featuresEnabled, int featuresTotal, long institutions) {}

    public record Stats(int modules, int modulesEnabled, int features, int featuresEnabled, long institutionsUsing, long institutionsNewThisMonth, long customConfigs, long updatedThisMonth) {}

    public record Overview(Stats stats, List<ModuleRow> modules) {}

    public record FeatureRow(String code, String name, String description, boolean enabled, boolean defaultEnabled) {}

    public record Detail(ModuleRow module, String recordNoun, List<FeatureRow> features, Map<String, String> settings) {}

    public record SaveBody(Map<String, Boolean> features, Map<String, String> settings) {}

    public record MetaBody(String name, String category, String description) {}

    public record StatusBody(Boolean enabled) {}

    public record BulkBody(Map<String, Boolean> states) {}

    public record AccessRow(Long institutionId, String institutionName, boolean enabled) {}

    public record AccessBody(Boolean enabled) {}

    public record Usage(long institutions, long totalInstitutions, int featuresEnabled, int featuresTotal, String lastChanged) {}

    public record AuditItem(String at, String actor, String description) {}
}
