-- Timetable settings, day structure, holidays, rules, break types and break rules (one institution each).
CREATE TABLE timetable_settings (
    institution_id          BIGINT PRIMARY KEY REFERENCES institutions (id) ON DELETE CASCADE,
    config_name             VARCHAR(80)  NOT NULL DEFAULT 'Default Period Config',
    academic_year_id        BIGINT REFERENCES academic_years (id) ON DELETE SET NULL,
    description             VARCHAR(500),
    time_format             VARCHAR(4)   NOT NULL DEFAULT 'H12',
    period_naming           VARCHAR(10)  NOT NULL DEFAULT 'PERIOD_N',
    default_duration        INT          NOT NULL DEFAULT 45,
    allow_variable_duration BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_zero_period       BOOLEAN      NOT NULL DEFAULT FALSE,
    allow_extra_period      BOOLEAN      NOT NULL DEFAULT FALSE,
    enable_breaks           BOOLEAN      NOT NULL DEFAULT TRUE,
    enable_lunch_break      BOOLEAN      NOT NULL DEFAULT TRUE,
    show_period_number      BOOLEAN      NOT NULL DEFAULT TRUE,
    half_day_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    half_day_end_time       TIME         NOT NULL DEFAULT '12:00',
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE timetable_slots (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(60) NOT NULL,
    slot_type      VARCHAR(10) NOT NULL DEFAULT 'TEACHING',
    start_time     TIME        NOT NULL,
    end_time       TIME        NOT NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_slot_times CHECK (end_time > start_time)
);
CREATE UNIQUE INDEX uq_slot_name ON timetable_slots (institution_id, lower(name));

CREATE TABLE timetable_holidays (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(80) NOT NULL,
    holiday_type   VARCHAR(12) NOT NULL DEFAULT 'NATIONAL',
    start_date     DATE        NOT NULL,
    end_date       DATE        NOT NULL,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_holiday_dates CHECK (end_date >= start_date)
);

CREATE TABLE timetable_rule_settings (
    institution_id              BIGINT PRIMARY KEY REFERENCES institutions (id) ON DELETE CASCADE,
    max_periods_per_day         INT     NOT NULL DEFAULT 8,
    max_consecutive_teacher     INT     NOT NULL DEFAULT 4,
    min_break_between           INT     NOT NULL DEFAULT 10,
    max_consecutive_subject     INT     NOT NULL DEFAULT 2,
    allow_double_periods        BOOLEAN NOT NULL DEFAULT TRUE,
    allow_free_periods          BOOLEAN NOT NULL DEFAULT TRUE,
    prefer_morning_core         BOOLEAN NOT NULL DEFAULT FALSE,
    gen_avoid_teacher_conflicts BOOLEAN NOT NULL DEFAULT TRUE,
    gen_avoid_room_conflicts    BOOLEAN NOT NULL DEFAULT TRUE,
    gen_distribute_evenly       BOOLEAN NOT NULL DEFAULT TRUE,
    gen_weekly_frequency        BOOLEAN NOT NULL DEFAULT TRUE,
    gen_working_hours           BOOLEAN NOT NULL DEFAULT TRUE,
    gen_preferred_slots         BOOLEAN NOT NULL DEFAULT TRUE,
    gen_manual_adjust           BOOLEAN NOT NULL DEFAULT TRUE,
    gen_avoid_consecutive       BOOLEAN NOT NULL DEFAULT TRUE,
    gen_daily_diversity         BOOLEAN NOT NULL DEFAULT TRUE,
    gen_teacher_workload        BOOLEAN NOT NULL DEFAULT TRUE,
    gen_ai_suggestions          BOOLEAN NOT NULL DEFAULT FALSE,
    rules_description           VARCHAR(500),
    on_teacher_conflict         VARCHAR(20) NOT NULL DEFAULT 'SUGGEST_ALTERNATIVE',
    on_room_conflict            VARCHAR(20) NOT NULL DEFAULT 'SUGGEST_ALTERNATIVE',
    on_subject_conflict         VARCHAR(20) NOT NULL DEFAULT 'NEXT_SLOT',
    on_limit_exceeded           VARCHAR(20) NOT NULL DEFAULT 'WARN_APPROVAL',
    conflict_order              VARCHAR(200) NOT NULL DEFAULT 'TEACHER_AVAILABILITY,CLASS_SUBJECT_REQUIREMENTS,ROOM_AVAILABILITY,SUBJECT_SEQUENCE,PREFERRED_SLOTS,EQUAL_DISTRIBUTION',
    adv_auto_resolve            BOOLEAN NOT NULL DEFAULT TRUE,
    adv_report                  BOOLEAN NOT NULL DEFAULT TRUE,
    adv_highlight               BOOLEAN NOT NULL DEFAULT TRUE,
    adv_manual_override         BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE timetable_rule_priorities (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    code           VARCHAR(30) NOT NULL,
    name           VARCHAR(80) NOT NULL,
    position       INT         NOT NULL,
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_rule_priority_code UNIQUE (institution_id, code)
);

CREATE TABLE break_types (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name             VARCHAR(60) NOT NULL,
    code             VARCHAR(10) NOT NULL,
    default_duration INT         NOT NULL,
    color            VARCHAR(7)  NOT NULL DEFAULT '#4CAF50',
    applicable_for   VARCHAR(80) NOT NULL DEFAULT 'TEACHING_PERIODS',
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    description      VARCHAR(300)
);
CREATE UNIQUE INDEX uq_break_type_name ON break_types (institution_id, lower(name));
CREATE UNIQUE INDEX uq_break_type_code ON break_types (institution_id, lower(code));

CREATE TABLE break_rules (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name             VARCHAR(80)  NOT NULL,
    break_type_id    BIGINT       NOT NULL REFERENCES break_types (id) ON DELETE RESTRICT,
    applies_to       VARCHAR(12)  NOT NULL DEFAULT 'ALL_CLASSES',
    class_from       INT,
    class_to         INT,
    start_time       TIME         NOT NULL,
    duration_minutes INT          NOT NULL,
    frequency        VARCHAR(14)  NOT NULL DEFAULT 'DAILY',
    days             VARCHAR(80),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    description      VARCHAR(500)
);
CREATE UNIQUE INDEX uq_break_rule_name ON break_rules (institution_id, lower(name));
