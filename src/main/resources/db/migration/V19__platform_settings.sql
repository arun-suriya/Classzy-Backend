-- Platform Settings page: logo and favicon files. The values themselves live in platform_settings (section 'PLATFORM').
CREATE TABLE platform_assets (
    kind         VARCHAR(10)  PRIMARY KEY,
    file_name    VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    data         BYTEA        NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   VARCHAR(200)
);
