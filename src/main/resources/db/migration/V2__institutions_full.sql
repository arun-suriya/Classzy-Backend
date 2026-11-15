-- Full institution profile (Add / Edit Institution form).
ALTER TABLE institutions
    ADD COLUMN lifecycle           VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN affiliation         VARCHAR(200),
    ADD COLUMN establishment_year  INT,
    ADD COLUMN description         TEXT,
    ADD COLUMN contact_name        VARCHAR(150),
    ADD COLUMN designation         VARCHAR(100),
    ADD COLUMN email               VARCHAR(200),
    ADD COLUMN phone_code          VARCHAR(8),
    ADD COLUMN phone               VARCHAR(20),
    ADD COLUMN alt_contact_name    VARCHAR(150),
    ADD COLUMN alt_designation     VARCHAR(100),
    ADD COLUMN alt_email           VARCHAR(200),
    ADD COLUMN alt_phone_code      VARCHAR(8),
    ADD COLUMN alt_phone           VARCHAR(20),
    ADD COLUMN address             VARCHAR(500),
    ADD COLUMN country             VARCHAR(80)  NOT NULL DEFAULT 'India',
    ADD COLUMN state               VARCHAR(80),
    ADD COLUMN city                VARCHAR(80),
    ADD COLUMN pin_code            VARCHAR(12),
    ADD COLUMN academic_start_month INT         NOT NULL DEFAULT 4,
    ADD COLUMN date_format         VARCHAR(20)  NOT NULL DEFAULT 'DD/MM/YYYY',
    ADD COLUMN time_zone           VARCHAR(80)  NOT NULL DEFAULT '(GMT+05:30) India Standard Time',
    ADD COLUMN language            VARCHAR(40)  NOT NULL DEFAULT 'English',
    ADD COLUMN grading_system      VARCHAR(20),
    ADD COLUMN currency            VARCHAR(30)  NOT NULL DEFAULT 'INR (₹)',
    ADD COLUMN start_time          TIME         NOT NULL DEFAULT '08:00',
    ADD COLUMN end_time            TIME         NOT NULL DEFAULT '15:00',
    ADD COLUMN plan_type           VARCHAR(20),
    ADD COLUMN subscription_start  DATE,
    ADD COLUMN subscription_end    DATE,
    ADD COLUMN expected_students   INT,
    ADD COLUMN expected_teachers   INT,
    ADD COLUMN updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ADD COLUMN archived_at         TIMESTAMPTZ;

UPDATE institutions SET lifecycle = CASE WHEN active THEN 'ACTIVE' ELSE 'ARCHIVED' END;
ALTER TABLE institutions DROP COLUMN active;

UPDATE institutions SET institution_type = 'SCHOOL'
 WHERE institution_type NOT IN ('SCHOOL','COLLEGE','UNIVERSITY','COACHING_CENTRE','OTHER');

CREATE UNIQUE INDEX uq_institution_name ON institutions (lower(name));

CREATE TABLE institution_modules (
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    module_code    VARCHAR(40) NOT NULL,
    PRIMARY KEY (institution_id, module_code)
);

CREATE TABLE institution_working_days (
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    day_of_week    VARCHAR(10) NOT NULL,
    PRIMARY KEY (institution_id, day_of_week)
);

-- Logo / banner kept apart so listing institutions never loads image bytes.
CREATE TABLE institution_images (
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    kind           VARCHAR(10) NOT NULL,
    content_type   VARCHAR(40) NOT NULL,
    data           BYTEA       NOT NULL,
    PRIMARY KEY (institution_id, kind)
);

INSERT INTO institution_working_days (institution_id, day_of_week)
SELECT i.id, d FROM institutions i, unnest(ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY']) AS d;
INSERT INTO institution_modules (institution_id, module_code)
SELECT i.id, m FROM institutions i,
       unnest(ARRAY['STUDENT_INFORMATION','ACADEMICS','ATTENDANCE','ASSIGNMENTS','EXAMS_RESULTS','FEES_PAYMENTS','LEAVE_MANAGEMENT','CLUBS_ACTIVITIES','COMMUNICATIONS']) AS m;
