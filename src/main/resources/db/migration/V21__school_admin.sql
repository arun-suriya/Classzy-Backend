-- School Admin portal: admissions, enquiries, student fees, staff leave and the school calendar.
-- Every table belongs to one institution. "sample" marks rows from the optional sample-data script so they can be removed.
CREATE TABLE school_enquiries (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(150) NOT NULL,
    phone          VARCHAR(30),
    email          VARCHAR(200),
    grade_interest VARCHAR(40),
    source         VARCHAR(40),
    status         VARCHAR(12)  NOT NULL DEFAULT 'NEW',
    note           VARCHAR(500),
    follow_up_on   DATE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sample         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_enq_inst ON school_enquiries (institution_id, status);

CREATE TABLE school_applications (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    application_no   VARCHAR(30)  NOT NULL,
    student_name     VARCHAR(150) NOT NULL,
    grade            VARCHAR(40),
    guardian_name    VARCHAR(150),
    phone            VARCHAR(30),
    stage            VARCHAR(20)  NOT NULL DEFAULT 'UNDER_REVIEW',
    academic_year_id BIGINT REFERENCES academic_years (id) ON DELETE SET NULL,
    submitted_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sample           BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_app_no UNIQUE (institution_id, application_no)
);
CREATE INDEX ix_app_inst ON school_applications (institution_id, stage);

CREATE TABLE school_fee_records (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT         NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    student_id       BIGINT         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    academic_year_id BIGINT         NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    term_id          BIGINT REFERENCES terms (id) ON DELETE SET NULL,
    fee_type         VARCHAR(60)    NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    due_date         DATE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    sample           BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_fee_inst ON school_fee_records (institution_id, academic_year_id);

CREATE TABLE school_fee_payments (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT         NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    fee_record_id  BIGINT         NOT NULL REFERENCES school_fee_records (id) ON DELETE CASCADE,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_on        DATE           NOT NULL DEFAULT CURRENT_DATE,
    method         VARCHAR(20)    NOT NULL DEFAULT 'CASH',
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    sample         BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_pay_record ON school_fee_payments (fee_record_id);

CREATE TABLE school_fee_concessions (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT         NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    student_id     BIGINT         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    fee_record_id  BIGINT REFERENCES school_fee_records (id) ON DELETE SET NULL,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    reason         VARCHAR(300),
    status         VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    requested_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    decided_by     VARCHAR(200),
    decided_at     TIMESTAMPTZ,
    sample         BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_conc_inst ON school_fee_concessions (institution_id, status);

CREATE TABLE school_leave_requests (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    teacher_id     BIGINT      NOT NULL REFERENCES teachers (id) ON DELETE CASCADE,
    leave_type     VARCHAR(20) NOT NULL DEFAULT 'CASUAL',
    from_date      DATE        NOT NULL,
    to_date        DATE        NOT NULL,
    reason         VARCHAR(300),
    status         VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    decided_by     VARCHAR(200),
    decided_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sample         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_leave_dates CHECK (to_date >= from_date)
);
CREATE INDEX ix_leave_inst ON school_leave_requests (institution_id, status);

CREATE TABLE school_events (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    title          VARCHAR(150) NOT NULL,
    event_date     DATE         NOT NULL,
    start_time     TIME,
    location       VARCHAR(100),
    category       VARCHAR(12)  NOT NULL DEFAULT 'OTHER',
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sample         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_event_inst ON school_events (institution_id, event_date);
