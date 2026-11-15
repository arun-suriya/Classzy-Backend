-- Classzy schema v1. Every tenant table carries institution_id (one shared database).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE institutions (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    institution_type VARCHAR(40)  NOT NULL DEFAULT 'SCHOOL',
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT REFERENCES institutions (id),      -- NULL for Super Admin
    email          VARCHAR(200) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(200) NOT NULL,
    role           VARCHAR(30)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE teachers (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id),
    full_name      VARCHAR(200) NOT NULL,
    email          VARCHAR(200),
    phone          VARCHAR(30),
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_teachers_inst ON teachers (institution_id);

CREATE TABLE academic_years (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id),
    name           VARCHAR(40)  NOT NULL,
    short_name     VARCHAR(40),
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    is_current     BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    description    VARCHAR(500),
    CONSTRAINT chk_year_dates CHECK (end_date > start_date),
    CONSTRAINT uq_year_name UNIQUE (institution_id, name),
    -- no two years of one institution may overlap
    CONSTRAINT ex_year_overlap EXCLUDE USING gist (
        institution_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    )
);
-- at most one current year per institution
CREATE UNIQUE INDEX uq_year_current ON academic_years (institution_id) WHERE is_current;

CREATE TABLE terms (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT        NOT NULL REFERENCES institutions (id),
    academic_year_id BIGINT        NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    name             VARCHAR(60)   NOT NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    weightage        NUMERIC(5, 2) NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    description      VARCHAR(500),
    CONSTRAINT chk_term_dates CHECK (end_date > start_date),
    CONSTRAINT chk_term_weight CHECK (weightage BETWEEN 0 AND 100),
    CONSTRAINT uq_term_name UNIQUE (academic_year_id, name)
);
CREATE INDEX ix_terms_year ON terms (academic_year_id);

CREATE TABLE grading_systems (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id),
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(300),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_grading_name UNIQUE (institution_id, name)
);

CREATE TABLE grades (
    id                BIGSERIAL PRIMARY KEY,
    institution_id    BIGINT       NOT NULL REFERENCES institutions (id),
    grading_system_id BIGINT       NOT NULL REFERENCES grading_systems (id) ON DELETE CASCADE,
    grade             VARCHAR(10)  NOT NULL,
    marks_from        INTEGER      NOT NULL,
    marks_to          INTEGER      NOT NULL,
    grade_point       NUMERIC(4, 1) NOT NULL DEFAULT 0,
    description       VARCHAR(100),
    CONSTRAINT chk_grade_range CHECK (marks_from >= 0 AND marks_to <= 100 AND marks_from <= marks_to),
    CONSTRAINT uq_grade_label UNIQUE (grading_system_id, grade),
    -- mark ranges inside one grading system may not overlap
    CONSTRAINT ex_grade_overlap EXCLUDE USING gist (
        grading_system_id WITH =,
        int4range(marks_from, marks_to, '[]') WITH &&
    )
);

CREATE TABLE subjects (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id),
    academic_year_id BIGINT       NOT NULL REFERENCES academic_years (id),
    name             VARCHAR(100) NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    category         VARCHAR(30)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    description      VARCHAR(500),
    CONSTRAINT uq_subject_code UNIQUE (academic_year_id, code)
);
CREATE INDEX ix_subjects_year ON subjects (academic_year_id);

CREATE TABLE subject_grade_levels (
    subject_id  BIGINT  NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    grade_level INTEGER NOT NULL,
    PRIMARY KEY (subject_id, grade_level)
);

CREATE TABLE school_classes (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id),
    academic_year_id BIGINT       NOT NULL REFERENCES academic_years (id),
    name             VARCHAR(60)  NOT NULL,
    grade_level      INTEGER,                                  -- NULL for pre-primary (LKG/UKG)
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    description      VARCHAR(500),
    CONSTRAINT uq_class_name UNIQUE (academic_year_id, name)
);
CREATE INDEX ix_classes_year ON school_classes (academic_year_id);

CREATE TABLE sections (
    id              BIGSERIAL PRIMARY KEY,
    institution_id  BIGINT       NOT NULL REFERENCES institutions (id),
    school_class_id BIGINT       NOT NULL REFERENCES school_classes (id),
    name            VARCHAR(40)  NOT NULL,
    teacher_id      BIGINT REFERENCES teachers (id),
    room            VARCHAR(60),
    max_students    INTEGER,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(500),
    CONSTRAINT chk_section_max CHECK (max_students IS NULL OR max_students >= 1),
    CONSTRAINT uq_section_name UNIQUE (school_class_id, name)
);
CREATE INDEX ix_sections_class ON sections (school_class_id);

CREATE TABLE students (
    id              BIGSERIAL PRIMARY KEY,
    institution_id  BIGINT       NOT NULL REFERENCES institutions (id),
    admission_no    VARCHAR(40)  NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    school_class_id BIGINT REFERENCES school_classes (id),
    section_id      BIGINT REFERENCES sections (id),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_student_admission UNIQUE (institution_id, admission_no)
);
CREATE INDEX ix_students_class ON students (school_class_id);
CREATE INDEX ix_students_section ON students (section_id);

CREATE TABLE promotion_rules (
    id                    BIGSERIAL PRIMARY KEY,
    institution_id        BIGINT       NOT NULL REFERENCES institutions (id),
    academic_year_id      BIGINT       NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    name                  VARCHAR(100) NOT NULL,
    from_class            VARCHAR(60)  NOT NULL,
    to_class              VARCHAR(60)  NOT NULL,
    criteria_type         VARCHAR(30)  NOT NULL,
    min_value             INTEGER,
    subject_scope         VARCHAR(30)  NOT NULL,
    criteria              VARCHAR(300) NOT NULL,
    additional_conditions VARCHAR(500),
    active                BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_rules_year ON promotion_rules (academic_year_id);

CREATE TABLE student_progressions (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT      NOT NULL REFERENCES institutions (id),
    academic_year_id BIGINT      NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    student_id       BIGINT      NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    next_class       VARCHAR(60),
    status           VARCHAR(20) NOT NULL,
    remarks          VARCHAR(300),
    CONSTRAINT uq_progression UNIQUE (academic_year_id, student_id)
);
