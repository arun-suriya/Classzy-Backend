-- School Admin › Settings. General details live on institutions; period length lives in timetable_settings. Everything else is here.
CREATE TABLE school_settings (
    institution_id        BIGINT PRIMARY KEY REFERENCES institutions (id) ON DELETE CASCADE,
    attendance_per_period BOOLEAN       NOT NULL DEFAULT TRUE,
    late_marking_allowed  BOOLEAN       NOT NULL DEFAULT TRUE,
    late_marking_minutes  INT           NOT NULL DEFAULT 15 CHECK (late_marking_minutes BETWEEN 1 AND 120),
    admission_year_id     BIGINT REFERENCES academic_years (id) ON DELETE SET NULL,
    application_opens     DATE,
    application_closes    DATE,
    required_documents    VARCHAR(400)  NOT NULL DEFAULT 'Birth Certificate,Previous School Report,ID Proof',
    approval_workflow     VARCHAR(12)   NOT NULL DEFAULT 'STANDARD',
    billing_frequency     VARCHAR(12)   NOT NULL DEFAULT 'TERM',
    due_date_rule         VARCHAR(12)   NOT NULL DEFAULT 'FIRST_15',
    late_fee_rule         VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    late_fee_amount       NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (late_fee_amount >= 0),
    receipt_prefix        VARCHAR(20)   NOT NULL DEFAULT 'RCPT/',
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_admission_window CHECK (application_closes IS NULL OR application_opens IS NULL OR application_closes >= application_opens)
);

-- Which provider the school has picked for each service. No credentials are stored and nothing connects to them yet.
CREATE TABLE school_integrations (
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    service        VARCHAR(10) NOT NULL,
    provider       VARCHAR(30) NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (institution_id, service)
);
