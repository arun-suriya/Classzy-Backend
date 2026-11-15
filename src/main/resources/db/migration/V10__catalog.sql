-- Generic per-institution lists: library categories/statuses/rules, custom fields, integrations.
CREATE TABLE catalog_items (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    kind           VARCHAR(30)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    code           VARCHAR(40),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    position       INT          NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_catalog_name ON catalog_items (institution_id, kind, lower(name));
CREATE UNIQUE INDEX uq_catalog_code ON catalog_items (institution_id, kind, lower(code)) WHERE code IS NOT NULL;

CREATE TABLE catalog_item_attrs (
    item_id    BIGINT       NOT NULL REFERENCES catalog_items (id) ON DELETE CASCADE,
    attr_key   VARCHAR(40)  NOT NULL,
    attr_value VARCHAR(1000) NOT NULL,
    PRIMARY KEY (item_id, attr_key)
);
