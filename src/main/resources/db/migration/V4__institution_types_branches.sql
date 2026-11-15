-- Editable institution types, and branches / campuses of an institution.
CREATE TABLE institution_types (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(300),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    system_type BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_institution_type_name ON institution_types (lower(name));

-- the five types that already existed as a fixed list
INSERT INTO institution_types (code, name, description, system_type) VALUES
    ('SCHOOL',          'School',          'Primary and secondary schools (K-12).', TRUE),
    ('COLLEGE',         'College',         'Colleges offering degree and diploma programmes.', TRUE),
    ('UNIVERSITY',      'University',      'Universities and deemed universities.', TRUE),
    ('COACHING_CENTRE', 'Coaching Centre', 'Coaching and tuition centres.', TRUE),
    ('OTHER',           'Other',           'Any institution that fits none of the above.', TRUE);

ALTER TABLE institutions
    ADD CONSTRAINT fk_institution_type FOREIGN KEY (institution_type) REFERENCES institution_types (code);

CREATE TABLE branches (
    id                 BIGSERIAL PRIMARY KEY,
    institution_id     BIGINT       NOT NULL REFERENCES institutions (id),
    name               VARCHAR(150) NOT NULL,
    code               VARCHAR(30),
    branch_type        VARCHAR(10)  NOT NULL DEFAULT 'BRANCH',
    is_main            BOOLEAN      NOT NULL DEFAULT FALSE,
    status             VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    head_name          VARCHAR(150),
    phone              VARCHAR(20),
    email              VARCHAR(200),
    address            VARCHAR(500),
    city               VARCHAR(80),
    state              VARCHAR(80),
    pin_code           VARCHAR(12),
    establishment_year INT,
    notes              VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_branches_inst ON branches (institution_id);
CREATE UNIQUE INDEX uq_branch_name ON branches (institution_id, lower(name));
CREATE UNIQUE INDEX uq_branch_code ON branches (institution_id, lower(code)) WHERE code IS NOT NULL;
-- one main branch per institution
CREATE UNIQUE INDEX uq_branch_main ON branches (institution_id) WHERE is_main;
