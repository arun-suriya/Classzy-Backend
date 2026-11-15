package com.centram.classzy.catalog;

import java.util.Map;

public final class CatalogDto {
    private CatalogDto() {}

    public record ItemBody(String name, String code, Boolean active, Map<String, String> attrs) {}

    public record ItemView(Long id, String name, String code, boolean active, int position, Map<String, String> attrs) {}
}
