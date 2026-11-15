-- Support & Tickets: tickets raised for institutions, their conversation, history, attachments and a small knowledge base.
CREATE SEQUENCE support_ticket_seq START 1;

CREATE TABLE support_tickets (
    id             BIGSERIAL PRIMARY KEY,
    ticket_no      VARCHAR(20)  NOT NULL UNIQUE,
    subject        VARCHAR(200) NOT NULL,
    description    TEXT,
    category       VARCHAR(40)  NOT NULL,
    priority       VARCHAR(8)   NOT NULL DEFAULT 'MEDIUM',
    status         VARCHAR(18)  NOT NULL DEFAULT 'OPEN',
    institution_id BIGINT REFERENCES institutions (id) ON DELETE SET NULL,
    requester      VARCHAR(150),
    assigned_to    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    escalated      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ,
    closed_at      TIMESTAMPTZ,
    CONSTRAINT chk_ticket_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT chk_ticket_status CHECK (status IN ('OPEN','IN_PROGRESS','AWAITING_CUSTOMER','RESOLVED','CLOSED'))
);
CREATE INDEX ix_tickets_status ON support_tickets (status);
CREATE INDEX ix_tickets_assigned ON support_tickets (assigned_to);
CREATE INDEX ix_tickets_created ON support_tickets (created_at DESC);

CREATE TABLE ticket_messages (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT       NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    author     VARCHAR(200),
    body       TEXT         NOT NULL,
    internal   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_messages ON ticket_messages (ticket_id, created_at);

CREATE TABLE ticket_events (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT       NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    actor      VARCHAR(200),
    text       VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_events ON ticket_events (ticket_id, created_at DESC);

CREATE TABLE ticket_attachments (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    file_name    VARCHAR(200) NOT NULL,
    content_type VARCHAR(100),
    size_bytes   BIGINT       NOT NULL,
    data         BYTEA        NOT NULL,
    uploaded_by  VARCHAR(200),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_attachments ON ticket_attachments (ticket_id);

CREATE TABLE kb_articles (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    category   VARCHAR(40)  NOT NULL,
    body       TEXT         NOT NULL,
    published  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
