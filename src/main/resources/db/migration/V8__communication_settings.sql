-- Communication settings: key/value settings per section, SMS sender ids, message templates, push categories.
CREATE TABLE comm_settings (
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    section        VARCHAR(30)  NOT NULL,
    setting_key    VARCHAR(60)  NOT NULL,
    setting_value  VARCHAR(2000),
    PRIMARY KEY (institution_id, section, setting_key)
);

CREATE TABLE sms_sender_ids (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    sender_id      VARCHAR(20) NOT NULL,
    sender_type    VARCHAR(15) NOT NULL DEFAULT 'ALPHANUMERIC',
    status         VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    default_sender BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_sms_sender ON sms_sender_ids (institution_id, lower(sender_id));

CREATE TABLE comm_templates (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    channel        VARCHAR(5)   NOT NULL,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    title          VARCHAR(150),
    body           VARCHAR(2000) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at     TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_comm_template ON comm_templates (institution_id, channel, lower(name));

CREATE TABLE push_categories (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(60)  NOT NULL,
    description    VARCHAR(200),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    mobile         BOOLEAN      NOT NULL DEFAULT TRUE,
    web            BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_push_category ON push_categories (institution_id, lower(name));
