-- Student Management: profile fields on students, plus uploaded documents.
ALTER TABLE students ADD COLUMN dob               DATE;
ALTER TABLE students ADD COLUMN joining_date      DATE;
ALTER TABLE students ADD COLUMN guardian_name     VARCHAR(200);
ALTER TABLE students ADD COLUMN guardian_relation VARCHAR(30);
ALTER TABLE students ADD COLUMN guardian_phone    VARCHAR(30);
ALTER TABLE students ADD COLUMN guardian_email    VARCHAR(200);
ALTER TABLE students ADD COLUMN address           VARCHAR(500);
ALTER TABLE students ADD COLUMN inactive_reason   VARCHAR(200);
ALTER TABLE students ADD COLUMN updated_at        TIMESTAMPTZ;

CREATE TABLE student_documents (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    student_id     BIGINT       NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    doc_type       VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    file_name      VARCHAR(200) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    data           BYTEA        NOT NULL,
    uploaded_by    VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_student_docs ON student_documents (student_id);
CREATE INDEX ix_students_inst_name ON students (institution_id, lower(full_name));
