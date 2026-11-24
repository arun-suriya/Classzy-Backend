CREATE TABLE school_saved_reports (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    source         VARCHAR(14)  NOT NULL,
    columns        VARCHAR(400) NOT NULL,
    year_id        BIGINT REFERENCES academic_years (id) ON DELETE SET NULL,
    class_id       BIGINT REFERENCES school_classes (id) ON DELETE SET NULL,
    section_id     BIGINT REFERENCES sections (id) ON DELETE SET NULL,
    created_by     VARCHAR(120),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_school_saved_report ON school_saved_reports (institution_id, lower(name));
