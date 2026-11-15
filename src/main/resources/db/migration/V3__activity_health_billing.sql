-- Activity log, system-health history and subscription payments.
CREATE TABLE audit_logs (
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_email    VARCHAR(200),
    actor_role     VARCHAR(30),
    action         VARCHAR(40)  NOT NULL,
    entity_type    VARCHAR(40),
    entity_id      BIGINT,
    institution_id BIGINT,
    description    VARCHAR(500) NOT NULL
);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);

CREATE TABLE health_logs (
    id              BIGSERIAL PRIMARY KEY,
    checked_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    overall         VARCHAR(16) NOT NULL,
    application_ok  BOOLEAN     NOT NULL,
    database_ok     BOOLEAN     NOT NULL,
    database_ms     INT,
    storage_ok      BOOLEAN     NOT NULL,
    memory_used_pct INT,
    disk_free_pct   INT,
    detail          VARCHAR(500)
);
CREATE INDEX ix_health_checked ON health_logs (checked_at DESC);

CREATE TABLE payments (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT         NOT NULL REFERENCES institutions (id),
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_on        DATE           NOT NULL,
    plan_type      VARCHAR(20),
    method         VARCHAR(20)    NOT NULL DEFAULT 'BANK_TRANSFER',
    reference      VARCHAR(100),
    note           VARCHAR(300),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_inst ON payments (institution_id);
CREATE INDEX ix_payments_paid ON payments (paid_on DESC);
