-- School attendance: daily student marks and student leave requests.
-- (Attendance *settings* — types, rules, thresholds — live in V5 and are only read here.)
CREATE TABLE school_att_leaves (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    student_id     BIGINT       NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    leave_type     VARCHAR(12)  NOT NULL DEFAULT 'MEDICAL',
    from_date      DATE         NOT NULL,
    to_date        DATE         NOT NULL,
    reason         VARCHAR(300),
    status         VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_by     VARCHAR(200),
    decided_at     TIMESTAMPTZ,
    CONSTRAINT ck_att_leave_dates  CHECK (to_date >= from_date),
    CONSTRAINT ck_att_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX ix_att_leave_inst ON school_att_leaves (institution_id, status, from_date);
CREATE INDEX ix_att_leave_student ON school_att_leaves (student_id);

CREATE TABLE school_att_marks (
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    student_id     BIGINT       NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    att_date       DATE         NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    remarks        VARCHAR(200),
    leave_id       BIGINT REFERENCES school_att_leaves (id) ON DELETE SET NULL,
    marked_by      VARCHAR(200),
    marked_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, att_date),
    CONSTRAINT ck_att_mark_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'ON_LEAVE'))
);
CREATE INDEX ix_att_mark_day ON school_att_marks (institution_id, att_date);
CREATE INDEX ix_att_mark_leave ON school_att_marks (leave_id) WHERE leave_id IS NOT NULL;
