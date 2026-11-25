-- Staff management builds on the existing teachers table (every staff member is a row there) and school_leave_requests.
CREATE TABLE school_departments (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(80) NOT NULL,
    head_id        BIGINT REFERENCES teachers (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_school_dept_name ON school_departments (institution_id, lower(name));

ALTER TABLE teachers ADD COLUMN staff_code    VARCHAR(20);
ALTER TABLE teachers ADD COLUMN staff_type    VARCHAR(12) NOT NULL DEFAULT 'TEACHING';
ALTER TABLE teachers ADD COLUMN designation   VARCHAR(60);
ALTER TABLE teachers ADD COLUMN department_id BIGINT REFERENCES school_departments (id) ON DELETE SET NULL;
ALTER TABLE teachers ADD COLUMN joining_date  DATE;
ALTER TABLE teachers ADD CONSTRAINT ck_teacher_staff_type CHECK (staff_type IN ('TEACHING', 'NON_TEACHING'));

UPDATE teachers t SET staff_code = 'STF-' || lpad(n.rn::text, 4, '0')
FROM (SELECT id, row_number() OVER (PARTITION BY institution_id ORDER BY id) AS rn FROM teachers) n
WHERE n.id = t.id;
CREATE UNIQUE INDEX uq_teacher_staff_code ON teachers (institution_id, lower(staff_code));
CREATE INDEX ix_teacher_dept ON teachers (department_id);

CREATE TABLE school_staff_cycles (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(60) NOT NULL,
    due_date       DATE        NOT NULL,
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_staff_cycle_name ON school_staff_cycles (institution_id, lower(name));

CREATE TABLE school_staff_appraisals (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    cycle_id       BIGINT       NOT NULL REFERENCES school_staff_cycles (id) ON DELETE CASCADE,
    teacher_id     BIGINT       NOT NULL REFERENCES teachers (id) ON DELETE CASCADE,
    reviewer_id    BIGINT REFERENCES teachers (id) ON DELETE SET NULL,
    due_date       DATE         NOT NULL,
    status         VARCHAR(12)  NOT NULL DEFAULT 'NOT_STARTED',
    rating         NUMERIC(2, 1),
    comments       VARCHAR(1000),
    completed_at   TIMESTAMPTZ,
    CONSTRAINT uq_appraisal UNIQUE (cycle_id, teacher_id),
    CONSTRAINT ck_appraisal_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_appraisal_rating CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5))
);
CREATE INDEX ix_appraisal_cycle ON school_staff_appraisals (cycle_id);

CREATE TABLE school_staff_trainings (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    title          VARCHAR(150) NOT NULL,
    training_date  DATE         NOT NULL,
    start_time     TIME,
    venue          VARCHAR(100),
    trainer        VARCHAR(100),
    description    VARCHAR(500),
    cancelled      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_training_inst ON school_staff_trainings (institution_id, training_date);
CREATE TABLE school_staff_training_people (
    training_id BIGINT NOT NULL REFERENCES school_staff_trainings (id) ON DELETE CASCADE,
    teacher_id  BIGINT NOT NULL REFERENCES teachers (id) ON DELETE CASCADE,
    PRIMARY KEY (training_id, teacher_id)
);

CREATE TABLE school_staff_vacancies (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    position       VARCHAR(100) NOT NULL,
    department_id  BIGINT REFERENCES school_departments (id) ON DELETE SET NULL,
    openings       INT          NOT NULL DEFAULT 1 CHECK (openings >= 1),
    description    VARCHAR(1000),
    closing_date   DATE,
    status         VARCHAR(8)   NOT NULL DEFAULT 'OPEN',
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_vacancy_status CHECK (status IN ('OPEN', 'CLOSED'))
);
CREATE INDEX ix_vacancy_inst ON school_staff_vacancies (institution_id, status);
CREATE TABLE school_staff_applicants (
    id          BIGSERIAL PRIMARY KEY,
    vacancy_id  BIGINT       NOT NULL REFERENCES school_staff_vacancies (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(200),
    phone       VARCHAR(30),
    status      VARCHAR(12)  NOT NULL DEFAULT 'APPLIED',
    notes       VARCHAR(300),
    applied_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_applicant_status CHECK (status IN ('APPLIED', 'SHORTLISTED', 'REJECTED', 'HIRED'))
);
CREATE INDEX ix_applicant_vacancy ON school_staff_applicants (vacancy_id);
