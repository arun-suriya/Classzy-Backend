-- Reports & Analytics: generated reports (stored as CSV) and recurring schedules.
ALTER TABLE students ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE report_schedules (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(150)  NOT NULL,
    template       VARCHAR(30)   NOT NULL,
    format         VARCHAR(8)    NOT NULL DEFAULT 'PDF',
    frequency      VARCHAR(8)    NOT NULL,
    range_days     INT           NOT NULL DEFAULT 30,
    institution_id BIGINT REFERENCES institutions (id) ON DELETE CASCADE,
    recipients     VARCHAR(1000),
    status         VARCHAR(8)    NOT NULL DEFAULT 'ACTIVE',
    next_run       TIMESTAMPTZ   NOT NULL,
    last_run       TIMESTAMPTZ,
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_sched_freq CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY')),
    CONSTRAINT chk_sched_status CHECK (status IN ('ACTIVE','PAUSED'))
);

CREATE TABLE generated_reports (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    template       VARCHAR(30)  NOT NULL,
    format         VARCHAR(8)   NOT NULL DEFAULT 'PDF',
    range_days     INT,
    institution_id BIGINT REFERENCES institutions (id) ON DELETE SET NULL,
    status         VARCHAR(12)  NOT NULL DEFAULT 'COMPLETED',
    row_count      INT          NOT NULL DEFAULT 0,
    content        TEXT         NOT NULL,
    generated_by   VARCHAR(200),
    schedule_id    BIGINT REFERENCES report_schedules (id) ON DELETE SET NULL,
    generated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_genreports_at ON generated_reports (generated_at DESC);
