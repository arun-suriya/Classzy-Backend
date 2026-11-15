-- Extra grading-system fields used by the Exam & Grading Settings page (all have defaults, so the old page keeps working).
ALTER TABLE grading_systems
    ADD COLUMN grade_type      VARCHAR(12) NOT NULL DEFAULT 'GRADE_POINT',
    ADD COLUMN total_marks     INT         NOT NULL DEFAULT 100,
    ADD COLUMN class_from      INT,
    ADD COLUMN class_to        INT,
    ADD COLUMN pass_percentage INT         NOT NULL DEFAULT 33;

CREATE TABLE exam_types (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    academic_year_id BIGINT       NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    name             VARCHAR(80)  NOT NULL,
    code             VARCHAR(10)  NOT NULL,
    description      VARCHAR(300),
    class_from       INT,
    class_to         INT,
    weightage        INT          NOT NULL DEFAULT 0,
    category         VARCHAR(10)  NOT NULL DEFAULT 'INTERNAL',
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_exam_type_name ON exam_types (academic_year_id, lower(name));
CREATE UNIQUE INDEX uq_exam_type_code ON exam_types (academic_year_id, lower(code));

CREATE TABLE report_templates (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    academic_year_id BIGINT       NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    name             VARCHAR(80)  NOT NULL,
    report_type      VARCHAR(12)  NOT NULL,
    class_from       INT,
    class_to         INT,
    sections         VARCHAR(200) NOT NULL DEFAULT '',
    active           BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_report_template_name ON report_templates (academic_year_id, lower(name));

CREATE TABLE result_templates (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    academic_year_id BIGINT       NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    name             VARCHAR(80)  NOT NULL,
    calculation_type VARCHAR(14)  NOT NULL,
    class_from       INT,
    class_to         INT,
    rounding_rule    VARCHAR(14)  NOT NULL DEFAULT 'NEAREST_WHOLE',
    display_format   VARCHAR(14)  NOT NULL DEFAULT 'GRADE_AND_MARKS',
    active           BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_result_template_name ON result_templates (academic_year_id, lower(name));

CREATE TABLE result_template_exam_types (
    result_template_id BIGINT NOT NULL REFERENCES result_templates (id) ON DELETE CASCADE,
    exam_type_id       BIGINT NOT NULL REFERENCES exam_types (id) ON DELETE RESTRICT,
    PRIMARY KEY (result_template_id, exam_type_id)
);

CREATE TABLE exam_settings (
    institution_id          BIGINT PRIMARY KEY REFERENCES institutions (id) ON DELETE CASCADE,
    publication_method      VARCHAR(14) NOT NULL DEFAULT 'MANUAL',
    show_subject_rank       BOOLEAN     NOT NULL DEFAULT TRUE,
    allow_reevaluation      BOOLEAN     NOT NULL DEFAULT TRUE,
    default_grading_id      BIGINT REFERENCES grading_systems (id) ON DELETE SET NULL,
    max_marks_per_subject   INT         NOT NULL DEFAULT 100,
    consider_attendance     BOOLEAN     NOT NULL DEFAULT FALSE,
    teachers_enter_marks    BOOLEAN     NOT NULL DEFAULT TRUE,
    review_approval         BOOLEAN     NOT NULL DEFAULT TRUE,
    edit_before_publish     BOOLEAN     NOT NULL DEFAULT TRUE,
    notify_on_publish       BOOLEAN     NOT NULL DEFAULT TRUE,
    reevaluation_process    BOOLEAN     NOT NULL DEFAULT TRUE,
    approval_levels         VARCHAR(60) NOT NULL DEFAULT 'CLASS_TEACHER,PRINCIPAL',
    workflow_remarks        VARCHAR(500),
    show_grade_and_marks    BOOLEAN     NOT NULL DEFAULT TRUE,
    parent_access           BOOLEAN     NOT NULL DEFAULT TRUE,
    show_teacher_comments   BOOLEAN     NOT NULL DEFAULT TRUE,
    auto_generate_pdf       BOOLEAN     NOT NULL DEFAULT TRUE,
    show_attendance_summary BOOLEAN     NOT NULL DEFAULT TRUE,
    pdf_template            VARCHAR(10) NOT NULL DEFAULT 'DEFAULT',
    include_school_logo     BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
