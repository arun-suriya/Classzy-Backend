-- School Admin › Examinations. Marks are stored as entered; percentages and grades are worked out when read, from the school's current grade bands.
CREATE TABLE school_exams (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT        NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name             VARCHAR(120)  NOT NULL,
    academic_year_id BIGINT        NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    term_id          BIGINT REFERENCES terms (id) ON DELETE SET NULL,
    exam_type        VARCHAR(14)   NOT NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    grading_scale    VARCHAR(12)   NOT NULL DEFAULT 'PERCENTAGE',
    pass_percent     NUMERIC(5, 2) NOT NULL DEFAULT 35 CHECK (pass_percent BETWEEN 0 AND 100),
    status           VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    created_by       VARCHAR(120),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_school_exam_dates CHECK (end_date >= start_date)
);
CREATE UNIQUE INDEX uq_school_exam_name ON school_exams (institution_id, academic_year_id, lower(name));
CREATE INDEX ix_school_exams_inst ON school_exams (institution_id, academic_year_id);

CREATE TABLE school_exam_sections (
    exam_id    BIGINT NOT NULL REFERENCES school_exams (id) ON DELETE CASCADE,
    section_id BIGINT NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    PRIMARY KEY (exam_id, section_id)
);

CREATE TABLE school_exam_papers (
    id         BIGSERIAL PRIMARY KEY,
    exam_id    BIGINT        NOT NULL REFERENCES school_exams (id) ON DELETE CASCADE,
    class_id   BIGINT        NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    subject_id BIGINT        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    exam_date  DATE          NOT NULL,
    start_time TIME          NOT NULL,
    end_time   TIME          NOT NULL,
    room       VARCHAR(40),
    max_marks  NUMERIC(6, 2) NOT NULL DEFAULT 100 CHECK (max_marks > 0),
    CONSTRAINT uq_school_exam_paper UNIQUE (exam_id, class_id, subject_id),
    CONSTRAINT ck_school_paper_times CHECK (end_time > start_time)
);

CREATE TABLE school_exam_marks (
    exam_id    BIGINT NOT NULL REFERENCES school_exams (id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    marks      NUMERIC(6, 2) CHECK (marks >= 0),
    absent     BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (exam_id, subject_id, student_id),
    CONSTRAINT ck_school_marks_absent CHECK (NOT (absent AND marks IS NOT NULL))
);

-- Review state of one exam + subject + section: DRAFT -> SUBMITTED -> APPROVED. Reports use approved results only.
CREATE TABLE school_exam_results (
    exam_id      BIGINT      NOT NULL REFERENCES school_exams (id) ON DELETE CASCADE,
    subject_id   BIGINT      NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    section_id   BIGINT      NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED')),
    submitted_by VARCHAR(120),
    submitted_at TIMESTAMPTZ,
    reviewed_by  VARCHAR(120),
    reviewed_at  TIMESTAMPTZ,
    PRIMARY KEY (exam_id, subject_id, section_id)
);

CREATE TABLE school_grade_bands (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    scale          VARCHAR(12) NOT NULL DEFAULT 'PERCENTAGE',
    grade          VARCHAR(10) NOT NULL,
    min_pct        INT         NOT NULL CHECK (min_pct BETWEEN 0 AND 100),
    max_pct        INT         NOT NULL CHECK (max_pct BETWEEN 0 AND 100),
    remark         VARCHAR(60),
    CONSTRAINT ck_school_band_range CHECK (max_pct >= min_pct)
);
CREATE UNIQUE INDEX uq_school_band_grade ON school_grade_bands (institution_id, scale, lower(grade));
