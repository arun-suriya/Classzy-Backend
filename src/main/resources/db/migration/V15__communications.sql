-- Platform communications: announcements, email, in-app, SMS and push messages composed by the Super Admin.
CREATE TABLE communications (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(150) NOT NULL,
    description      VARCHAR(500),
    comm_type        VARCHAR(14)  NOT NULL,
    priority         VARCHAR(8)   NOT NULL DEFAULT 'NORMAL',
    audience_kind    VARCHAR(12)  NOT NULL DEFAULT 'ALL_USERS',
    audience_targets VARCHAR(2000),
    subject          VARCHAR(200),
    body             TEXT,
    status           VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    scheduled_at     TIMESTAMPTZ,
    sent_at          TIMESTAMPTZ,
    recipients       INT,
    created_by       VARCHAR(200),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_comm_type CHECK (comm_type IN ('ANNOUNCEMENT','EMAIL','IN_APP','SMS','PUSH')),
    CONSTRAINT chk_comm_status CHECK (status IN ('DRAFT','SCHEDULED','SENT'))
);
CREATE INDEX ix_comm_status ON communications (status);
CREATE INDEX ix_comm_type ON communications (comm_type);
