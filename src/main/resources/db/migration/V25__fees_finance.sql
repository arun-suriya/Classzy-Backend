-- School Admin > Fees & Finance: fee structures, generated invoices, receipts, concessions (%), scholarships and refunds.
-- Builds on school_fee_records (= invoices), school_fee_payments and school_fee_concessions from V21.

CREATE TABLE school_fee_structures (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT         NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    academic_year_id BIGINT         NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    class_id         BIGINT         NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    fee_head         VARCHAR(60)    NOT NULL,
    frequency        VARCHAR(10)    NOT NULL DEFAULT 'TERM',
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    sample           BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_fee_structure ON school_fee_structures (class_id, lower(fee_head));
CREATE INDEX ix_fee_structure_year ON school_fee_structures (institution_id, academic_year_id);

ALTER TABLE school_fee_records ADD COLUMN invoice_no   VARCHAR(20);
ALTER TABLE school_fee_records ADD COLUMN structure_id BIGINT REFERENCES school_fee_structures (id) ON DELETE SET NULL;
UPDATE school_fee_records r SET invoice_no = 'INV-' || lpad(x.n::text, 4, '0')
  FROM (SELECT id, row_number() OVER (PARTITION BY institution_id ORDER BY id) AS n FROM school_fee_records) x WHERE x.id = r.id;
ALTER TABLE school_fee_records ALTER COLUMN invoice_no SET NOT NULL;
CREATE UNIQUE INDEX uq_fee_invoice_no ON school_fee_records (institution_id, invoice_no);
-- generating invoices twice must not double-bill
CREATE UNIQUE INDEX uq_fee_generated ON school_fee_records (student_id, structure_id, COALESCE(term_id, 0)) WHERE structure_id IS NOT NULL;

ALTER TABLE school_fee_payments ADD COLUMN receipt_no   VARCHAR(20);
ALTER TABLE school_fee_payments ADD COLUMN reference_no VARCHAR(60);
ALTER TABLE school_fee_payments ADD COLUMN notes        VARCHAR(300);
ALTER TABLE school_fee_payments ADD COLUMN created_by   VARCHAR(200);
UPDATE school_fee_payments p SET receipt_no = 'RCPT-' || lpad(x.n::text, 4, '0')
  FROM (SELECT id, row_number() OVER (PARTITION BY institution_id ORDER BY id) AS n FROM school_fee_payments) x WHERE x.id = p.id;
ALTER TABLE school_fee_payments ALTER COLUMN receipt_no SET NOT NULL;
CREATE UNIQUE INDEX uq_fee_receipt_no ON school_fee_payments (institution_id, receipt_no);

ALTER TABLE school_fee_concessions ADD COLUMN percent NUMERIC(5, 2);

CREATE TABLE school_scholarships (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT         NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    student_id       BIGINT         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    academic_year_id BIGINT         NOT NULL REFERENCES academic_years (id) ON DELETE CASCADE,
    term_id          BIGINT REFERENCES terms (id) ON DELETE SET NULL,
    name             VARCHAR(100)   NOT NULL,
    award_amount     NUMERIC(12, 2) NOT NULL CHECK (award_amount > 0),
    validity         VARCHAR(10)    NOT NULL DEFAULT 'YEARLY',
    status           VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    awarded_by       VARCHAR(200),
    awarded_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    sample           BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_scholar_inst ON school_scholarships (institution_id, academic_year_id, status);

-- how much of a scholarship has been taken off which invoice
CREATE TABLE school_scholarship_allocations (
    scholarship_id BIGINT         NOT NULL REFERENCES school_scholarships (id) ON DELETE CASCADE,
    fee_record_id  BIGINT         NOT NULL REFERENCES school_fee_records (id) ON DELETE CASCADE,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    PRIMARY KEY (scholarship_id, fee_record_id)
);

CREATE TABLE school_fee_refunds (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT         NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    payment_id     BIGINT         NOT NULL REFERENCES school_fee_payments (id) ON DELETE CASCADE,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    reason         VARCHAR(300),
    status         VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    requested_by   VARCHAR(200),
    requested_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    decided_by     VARCHAR(200),
    decided_at     TIMESTAMPTZ,
    sample         BOOLEAN        NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_refund_inst ON school_fee_refunds (institution_id, requested_at);

-- One row per invoice with its running figures:
--   net     = amount - approved concessions - scholarship allocations
--   paid    = payments - refunds that were marked refunded
--   balance = net - paid
CREATE VIEW school_invoice_v AS
SELECT r.id, r.institution_id, r.student_id, r.academic_year_id, r.term_id, r.fee_type, r.amount, r.due_date, r.invoice_no, r.structure_id,
       c.v AS concession, s.v AS scholarship, (r.amount - c.v - s.v) AS net, (p.v - f.v) AS paid, (r.amount - c.v - s.v - (p.v - f.v)) AS balance
FROM school_fee_records r
CROSS JOIN LATERAL (SELECT COALESCE(SUM(x.amount), 0) AS v FROM school_fee_concessions x WHERE x.fee_record_id = r.id AND x.status = 'APPROVED') c
CROSS JOIN LATERAL (SELECT COALESCE(SUM(x.amount), 0) AS v FROM school_scholarship_allocations x WHERE x.fee_record_id = r.id) s
CROSS JOIN LATERAL (SELECT COALESCE(SUM(x.amount), 0) AS v FROM school_fee_payments x WHERE x.fee_record_id = r.id) p
CROSS JOIN LATERAL (SELECT COALESCE(SUM(x.amount), 0) AS v FROM school_fee_refunds x JOIN school_fee_payments y ON y.id = x.payment_id WHERE y.fee_record_id = r.id AND x.status = 'REFUNDED') f;
