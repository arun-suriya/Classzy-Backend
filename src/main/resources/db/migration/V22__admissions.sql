-- Admissions module: richer applications, enquiry links, uploaded documents and offers.
ALTER TABLE school_applications
    ADD COLUMN dob            DATE,
    ADD COLUMN guardian_email VARCHAR(200),
    ADD COLUMN class_id       BIGINT REFERENCES school_classes (id) ON DELETE SET NULL,
    ADD COLUMN waitlisted_at  TIMESTAMPTZ,
    ADD COLUMN student_id     BIGINT REFERENCES students (id) ON DELETE SET NULL,
    ADD COLUMN admitted_at    TIMESTAMPTZ;
CREATE INDEX ix_app_class ON school_applications (class_id, stage);

ALTER TABLE school_enquiries
    ADD COLUMN child_name     VARCHAR(150),
    ADD COLUMN application_id BIGINT REFERENCES school_applications (id) ON DELETE SET NULL;

CREATE TABLE school_application_documents (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    application_id BIGINT       NOT NULL REFERENCES school_applications (id) ON DELETE CASCADE,
    doc_type       VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    file_name      VARCHAR(200) NOT NULL,
    content_type   VARCHAR(60)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    data           BYTEA        NOT NULL,
    verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_by    VARCHAR(200),
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_appdoc_app ON school_application_documents (application_id);

CREATE TABLE school_offers (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    application_id BIGINT      NOT NULL REFERENCES school_applications (id) ON DELETE CASCADE,
    expires_on     DATE        NOT NULL,
    status         VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    note           VARCHAR(300),
    sent_at        TIMESTAMPTZ,
    responded_at   TIMESTAMPTZ,
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sample         BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_offer_inst ON school_offers (institution_id, status);
-- one live offer per application
CREATE UNIQUE INDEX uq_offer_open ON school_offers (application_id) WHERE status IN ('PENDING', 'SENT', 'ACCEPTED');
