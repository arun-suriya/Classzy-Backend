-- System Management: instances/environments, platform integrations, security & system settings and data backups.
CREATE TABLE system_settings (
    setting_key   VARCHAR(60)   PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by    VARCHAR(200)
);

CREATE TABLE platform_instances (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    institution_id BIGINT REFERENCES institutions (id) ON DELETE SET NULL,
    kind           VARCHAR(12)  NOT NULL DEFAULT 'PRODUCTION',
    environment    VARCHAR(10)  NOT NULL DEFAULT 'PROD',
    version        VARCHAR(20),
    status         VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    last_refresh   TIMESTAMPTZ,
    notes          VARCHAR(500),
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_inst_kind CHECK (kind IN ('PRODUCTION','NON_PROD')),
    CONSTRAINT chk_inst_env CHECK (environment IN ('PROD','QA','PREVIEW','DEV')),
    CONSTRAINT chk_inst_status CHECK (status IN ('ACTIVE','REFRESHING','STOPPED'))
);

CREATE TABLE platform_integrations (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    integration_type VARCHAR(10)  NOT NULL DEFAULT 'API',
    source           VARCHAR(60),
    target           VARCHAR(60),
    endpoint         VARCHAR(300),
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    last_run         TIMESTAMPTZ,
    last_ms          INT,
    last_message     VARCHAR(300),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_integ_type CHECK (integration_type IN ('SAP','API','DEVICE','OTHER')),
    CONSTRAINT chk_integ_status CHECK (status IN ('ACTIVE','ISSUE','DISABLED'))
);

CREATE TABLE system_backups (
    id          BIGSERIAL PRIMARY KEY,
    backup_type VARCHAR(14)  NOT NULL,
    trigger     VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    status      VARCHAR(10)  NOT NULL,
    size_bytes  BIGINT       NOT NULL DEFAULT 0,
    table_count INT          NOT NULL DEFAULT 0,
    row_count   BIGINT       NOT NULL DEFAULT 0,
    note        VARCHAR(300),
    data        BYTEA        NOT NULL,
    created_by  VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_backup_type CHECK (backup_type IN ('FULL','DATABASE','CONFIGURATION')),
    CONSTRAINT chk_backup_status CHECK (status IN ('SUCCESS','FAILED'))
);
CREATE INDEX ix_backups_created ON system_backups (created_at DESC);
