-- Platform-wide billing settings and invoices. Payments (V3) stay the source of truth for revenue.
CREATE TABLE platform_settings (
    section       VARCHAR(40)  NOT NULL,
    setting_key   VARCHAR(60)  NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    PRIMARY KEY (section, setting_key)
);

CREATE TABLE invoices (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT         NOT NULL REFERENCES institutions (id),
    invoice_no     VARCHAR(30)    NOT NULL,
    issue_date     DATE           NOT NULL,
    due_date       DATE           NOT NULL,
    subtotal       NUMERIC(12, 2) NOT NULL CHECK (subtotal > 0),
    tax_amount     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount         NUMERIC(12, 2) NOT NULL,
    description    VARCHAR(300),
    paid_on        DATE,
    payment_id     BIGINT REFERENCES payments (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_no UNIQUE (invoice_no),
    CONSTRAINT chk_invoice_dates CHECK (due_date >= issue_date)
);
CREATE INDEX ix_invoices_inst ON invoices (institution_id);
CREATE INDEX ix_invoices_issue ON invoices (issue_date DESC);
