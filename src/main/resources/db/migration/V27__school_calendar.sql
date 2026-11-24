-- Extends the V21 school_events table (used by the dashboard's "Today's Schedule") into a full calendar.
-- event_date stays the start date. Old rows get end_date = event_date and event_type OTHER.
ALTER TABLE school_events ALTER COLUMN location TYPE VARCHAR(150);
ALTER TABLE school_events
    ADD COLUMN event_type  VARCHAR(16) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN all_day     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN end_date    DATE,
    ADD COLUMN end_time    TIME,
    ADD COLUMN audience    VARCHAR(10) NOT NULL DEFAULT 'EVERYONE',
    ADD COLUMN class_id    BIGINT REFERENCES school_classes (id) ON DELETE SET NULL,
    ADD COLUMN section_id  BIGINT REFERENCES sections (id) ON DELETE SET NULL,
    ADD COLUMN description VARCHAR(2000),
    ADD COLUMN reminder    VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN updated_at  TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE school_events SET end_date = event_date, all_day = (start_time IS NULL);
ALTER TABLE school_events ADD CONSTRAINT ck_event_dates CHECK (end_date IS NULL OR end_date >= event_date);
CREATE INDEX ix_event_range ON school_events (institution_id, event_date, end_date);
