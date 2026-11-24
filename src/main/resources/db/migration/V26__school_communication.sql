-- School Admin > Communication: messages (with a snapshot of who they were addressed to), templates, notifications and announcements.
-- Nothing here talks to an email/SMS provider: messages are recorded, not delivered.

CREATE TABLE school_messages (
    id              BIGSERIAL PRIMARY KEY,
    institution_id  BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    audience_kind   VARCHAR(14)  NOT NULL,
    class_id        BIGINT REFERENCES school_classes (id) ON DELETE SET NULL,
    section_id      BIGINT REFERENCES sections (id) ON DELETE SET NULL,
    audience_label  VARCHAR(100) NOT NULL,
    channel         VARCHAR(10)  NOT NULL,
    subject         VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'SENT',
    scheduled_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    recipient_count INT          NOT NULL DEFAULT 0,
    reachable_count INT          NOT NULL DEFAULT 0,
    note            VARCHAR(200),
    created_by      VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_msg_status CHECK (status IN ('SCHEDULED', 'SENT', 'CANCELLED'))
);
CREATE INDEX ix_msg_inst ON school_messages (institution_id, status, sent_at);
CREATE INDEX ix_msg_due ON school_messages (status, scheduled_at) WHERE status = 'SCHEDULED';

CREATE TABLE school_message_recipients (
    id           BIGSERIAL PRIMARY KEY,
    message_id   BIGINT       NOT NULL REFERENCES school_messages (id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    student_name VARCHAR(200),
    contact      VARCHAR(200),
    reachable    BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_msg_rcpt ON school_message_recipients (message_id);

CREATE TABLE school_msg_templates (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(12)  NOT NULL DEFAULT 'GENERAL',
    channel        VARCHAR(10)  NOT NULL DEFAULT 'EMAIL',
    subject        VARCHAR(200) NOT NULL,
    body           TEXT         NOT NULL,
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_school_msg_template ON school_msg_templates (institution_id, lower(name));

CREATE TABLE school_notifications (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    kind           VARCHAR(14)  NOT NULL,
    title          VARCHAR(150) NOT NULL,
    body           VARCHAR(400),
    link           VARCHAR(120),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notif_inst ON school_notifications (institution_id, created_at DESC);

CREATE TABLE school_notification_reads (
    notification_id BIGINT NOT NULL REFERENCES school_notifications (id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL,
    PRIMARY KEY (notification_id, user_id)
);

CREATE TABLE school_announcements (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    body           TEXT         NOT NULL,
    audience       VARCHAR(10)  NOT NULL DEFAULT 'EVERYONE',
    publish_date   DATE         NOT NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);
CREATE INDEX ix_ann_inst ON school_announcements (institution_id, publish_date DESC);
