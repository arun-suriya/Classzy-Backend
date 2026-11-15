-- User management: profile, status, validity, roles on top of the existing login table.
ALTER TABLE users
    ADD COLUMN user_type     VARCHAR(20) NOT NULL DEFAULT 'ADMINISTRATOR',
    ADD COLUMN status        VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN last_login_at TIMESTAMPTZ,
    ADD COLUMN valid_from    DATE,
    ADD COLUMN valid_to      DATE;
UPDATE users SET user_type = 'TEACHER' WHERE role = 'TEACHER';
UPDATE users SET status = 'INACTIVE' WHERE NOT active;

CREATE TABLE user_profiles (
    user_id              BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100),
    phone_code           VARCHAR(8),
    phone                VARCHAR(20),
    gender               VARCHAR(10),
    date_of_birth        DATE,
    notes                VARCHAR(500),
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    two_factor           BOOLEAN NOT NULL DEFAULT FALSE,
    reset_after_days     INT,
    password_changed_at  TIMESTAMPTZ,
    employee_id          VARCHAR(40),
    department           VARCHAR(80),
    designation          VARCHAR(80),
    joined_on            DATE,
    alt_email            VARCHAR(200),
    emergency_contact    VARCHAR(30),
    address1             VARCHAR(150),
    address2             VARCHAR(150),
    city                 VARCHAR(80),
    state                VARCHAR(80),
    pin_code             VARCHAR(12),
    data_scope           VARCHAR(10) NOT NULL DEFAULT 'ASSIGNED',
    academic_year_id     BIGINT REFERENCES academic_years (id) ON DELETE SET NULL,
    notify_events        VARCHAR(300),
    notify_language      VARCHAR(10) NOT NULL DEFAULT 'en',
    notify_timezone      VARCHAR(60) NOT NULL DEFAULT 'Asia/Kolkata',
    notify_via           VARCHAR(40) NOT NULL DEFAULT 'EMAIL,IN_APP'
);

CREATE TABLE user_roles (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code  VARCHAR(30) NOT NULL,
    is_primary BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, role_code)
);

-- existing accounts get a profile and a role
INSERT INTO user_profiles (user_id, first_name, last_name, phone_code)
SELECT id, split_part(full_name, ' ', 1), nullif(trim(substr(full_name, length(split_part(full_name, ' ', 1)) + 1)), ''), '+91' FROM users;
INSERT INTO user_roles (user_id, role_code, is_primary)
SELECT id, CASE role WHEN 'TEACHER' THEN 'TEACHER' ELSE 'ADMINISTRATOR' END, TRUE FROM users WHERE role <> 'SUPER_ADMIN';
