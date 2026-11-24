-- School Admin > Academics & Timetable: subjects per grade, weekly timetable, homework, assignments.
CREATE TABLE class_subjects (
    institution_id  BIGINT   NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    class_id        BIGINT   NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    subject_id      BIGINT   NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    periods_per_week SMALLINT NOT NULL DEFAULT 1,
    PRIMARY KEY (class_id, subject_id),
    CONSTRAINT ck_periods CHECK (periods_per_week BETWEEN 1 AND 20)
);

-- The weekly timetable: one row per section, period (timetable_slots) and weekday (1 = Monday ... 5 = Friday).
CREATE TABLE timetable_entries (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT   NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    section_id     BIGINT   NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    slot_id        BIGINT   NOT NULL REFERENCES timetable_slots (id) ON DELETE CASCADE,
    dow            SMALLINT NOT NULL,
    subject_id     BIGINT   NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    teacher_id     BIGINT   REFERENCES teachers (id) ON DELETE SET NULL,
    CONSTRAINT ck_dow CHECK (dow BETWEEN 1 AND 7),
    CONSTRAINT uq_tt_cell UNIQUE (section_id, slot_id, dow)
);
CREATE INDEX ix_tt_teacher ON timetable_entries (teacher_id, slot_id, dow);

CREATE TABLE timetable_publish (
    section_id     BIGINT PRIMARY KEY REFERENCES sections (id) ON DELETE CASCADE,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    status         VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    published_at   TIMESTAMPTZ,
    published_by   VARCHAR(200)
);

CREATE TABLE school_homework (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    section_id     BIGINT       NOT NULL REFERENCES sections (id),
    subject_id     BIGINT       NOT NULL REFERENCES subjects (id),
    title          VARCHAR(200) NOT NULL,
    description    VARCHAR(2000),
    due_date       DATE         NOT NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    sample         BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_hw_inst ON school_homework (institution_id, section_id, due_date);

CREATE TABLE school_assignments (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    section_id     BIGINT       NOT NULL REFERENCES sections (id),
    subject_id     BIGINT       NOT NULL REFERENCES subjects (id),
    title          VARCHAR(200) NOT NULL,
    description    VARCHAR(2000),
    due_date       DATE         NOT NULL,
    max_marks      INTEGER      NOT NULL DEFAULT 100,
    status         VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    sample         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_asg_marks CHECK (max_marks BETWEEN 1 AND 1000)
);
CREATE INDEX ix_asg_inst ON school_assignments (institution_id, section_id, due_date);

CREATE TABLE school_assignment_submissions (
    assignment_id  BIGINT        NOT NULL REFERENCES school_assignments (id) ON DELETE CASCADE,
    student_id     BIGINT        NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    institution_id BIGINT        NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    submitted_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    marks          NUMERIC(6, 2),
    remarks        VARCHAR(300),
    PRIMARY KEY (assignment_id, student_id)
);
