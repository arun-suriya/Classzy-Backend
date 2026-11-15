-- Attendance settings (one row per institution) and its lists: types, rules, notification events and templates.
CREATE TABLE attendance_settings (
    institution_id      BIGINT PRIMARY KEY REFERENCES institutions (id) ON DELETE CASCADE,
    attendance_mode     VARCHAR(12) NOT NULL DEFAULT 'DAILY',
    default_method      VARCHAR(12) NOT NULL DEFAULT 'MANUAL',
    default_mark        VARCHAR(10) NOT NULL DEFAULT 'PRESENT',
    late_after_minutes  INT         NOT NULL DEFAULT 15,
    half_day_minutes    INT         NOT NULL DEFAULT 180,
    biometric           BOOLEAN     NOT NULL DEFAULT TRUE,
    rfid                BOOLEAN     NOT NULL DEFAULT TRUE,
    mobile_app          BOOLEAN     NOT NULL DEFAULT TRUE,
    gps                 BOOLEAN     NOT NULL DEFAULT TRUE,
    offline_mode        BOOLEAN     NOT NULL DEFAULT FALSE,
    auto_sync           BOOLEAN     NOT NULL DEFAULT TRUE,
    parent_view         BOOLEAN     NOT NULL DEFAULT TRUE,
    student_view        BOOLEAN     NOT NULL DEFAULT TRUE,
    remarks_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    adjustment_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    min_attendance_pct  INT         NOT NULL DEFAULT 75,
    count_present       BOOLEAN     NOT NULL DEFAULT TRUE,
    count_late          BOOLEAN     NOT NULL DEFAULT TRUE,
    count_half_day      BOOLEAN     NOT NULL DEFAULT TRUE,
    count_on_leave      BOOLEAN     NOT NULL DEFAULT FALSE,
    count_sick_leave    BOOLEAN     NOT NULL DEFAULT FALSE,
    count_excused       BOOLEAN     NOT NULL DEFAULT FALSE,
    exclude_holidays    BOOLEAN     NOT NULL DEFAULT TRUE,
    exclude_week_offs   BOOLEAN     NOT NULL DEFAULT TRUE,
    grace_period_minutes INT        NOT NULL DEFAULT 5,
    auto_absent_time    TIME        NOT NULL DEFAULT '10:00',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attendance_types (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(60) NOT NULL,
    code           VARCHAR(10) NOT NULL,
    category       VARCHAR(12) NOT NULL,
    counts_as      VARCHAR(14) NOT NULL,
    color          VARCHAR(7)  NOT NULL DEFAULT '#6C757D',
    is_default     BOOLEAN     NOT NULL DEFAULT FALSE,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    description    VARCHAR(300),
    sort_order     INT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_att_type_name ON attendance_types (institution_id, lower(name));
CREATE UNIQUE INDEX uq_att_type_code ON attendance_types (institution_id, lower(code));
CREATE UNIQUE INDEX uq_att_type_default ON attendance_types (institution_id) WHERE is_default;

CREATE TABLE attendance_rules (
    id                  BIGSERIAL PRIMARY KEY,
    institution_id      BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name                VARCHAR(80)  NOT NULL,
    description         VARCHAR(300),
    applies_to          VARCHAR(14)  NOT NULL DEFAULT 'ALL_STUDENTS',
    priority            INT          NOT NULL DEFAULT 1,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    condition_field     VARCHAR(20)  NOT NULL,
    condition_operator  VARCHAR(20)  NOT NULL,
    condition_value     VARCHAR(10)  NOT NULL,
    action_type_code    VARCHAR(10)  NOT NULL,
    notify_parent       BOOLEAN      NOT NULL DEFAULT FALSE,
    include_in_reports  BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_att_rule_name ON attendance_rules (institution_id, lower(name));

CREATE TABLE attendance_templates (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT        NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(80)   NOT NULL,
    body           VARCHAR(1000) NOT NULL,
    active         BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_att_template_name ON attendance_templates (institution_id, lower(name));

CREATE TABLE attendance_notification_events (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(80)  NOT NULL,
    trigger_code   VARCHAR(24)  NOT NULL,
    recipients     VARCHAR(80)  NOT NULL,
    channels       VARCHAR(30)  NOT NULL,
    template_id    BIGINT REFERENCES attendance_templates (id),
    custom_message VARCHAR(1000),
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_att_event_name ON attendance_notification_events (institution_id, lower(name));
