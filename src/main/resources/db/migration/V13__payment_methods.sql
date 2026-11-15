-- Saved payment methods for the platform's billing. Only the brand, last 4 digits and expiry are kept; never a full card number.
CREATE TABLE payment_methods (
    id           BIGSERIAL PRIMARY KEY,
    brand        VARCHAR(20) NOT NULL,
    last4        CHAR(4)     NOT NULL,
    expiry_month INT         NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year  INT         NOT NULL,
    is_default   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_method UNIQUE (brand, last4, expiry_month, expiry_year)
);
CREATE UNIQUE INDEX uq_payment_method_default ON payment_methods (is_default) WHERE is_default;
