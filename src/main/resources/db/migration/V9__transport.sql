-- Transport: routes, stops, drivers, vehicles. (General transport settings live in comm_settings, section TRANSPORT.)
CREATE TABLE transport_routes (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    code             VARCHAR(20)  NOT NULL,
    start_location   VARCHAR(150) NOT NULL,
    end_location     VARCHAR(150) NOT NULL,
    distance_km      DOUBLE PRECISION,
    duration_minutes INT,
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    description      VARCHAR(500)
);
CREATE UNIQUE INDEX uq_transport_route_name ON transport_routes (institution_id, lower(name));
CREATE UNIQUE INDEX uq_transport_route_code ON transport_routes (institution_id, lower(code));

CREATE TABLE transport_stops (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    route_id       BIGINT       NOT NULL REFERENCES transport_routes (id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    code           VARCHAR(20),
    sequence_no    INT          NOT NULL,
    latitude       DOUBLE PRECISION,
    longitude      DOUBLE PRECISION,
    address        VARCHAR(300),
    arrival_time   TIME,
    departure_time TIME,
    radius_meters  INT          NOT NULL DEFAULT 100,
    stop_type      VARCHAR(12)  NOT NULL DEFAULT 'PICKUP_DROP',
    status         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
);
CREATE UNIQUE INDEX uq_transport_stop_name ON transport_stops (route_id, lower(name));
CREATE INDEX ix_transport_stop_route ON transport_stops (route_id, sequence_no);

CREATE TABLE transport_drivers (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    code             VARCHAR(20)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    mobile           VARCHAR(20)  NOT NULL,
    date_of_birth    DATE,
    gender           VARCHAR(10),
    address          VARCHAR(300),
    license_no       VARCHAR(40)  NOT NULL,
    license_expiry   DATE         NOT NULL,
    license_type     VARCHAR(10)  NOT NULL DEFAULT 'HMV',
    experience_years INT,
    emergency_name   VARCHAR(100),
    emergency_phone  VARCHAR(20),
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    remarks          VARCHAR(300)
);
CREATE UNIQUE INDEX uq_transport_driver_code ON transport_drivers (institution_id, lower(code));
CREATE UNIQUE INDEX uq_transport_driver_license ON transport_drivers (institution_id, lower(license_no));

CREATE TABLE transport_vehicles (
    id               BIGSERIAL PRIMARY KEY,
    institution_id   BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    vehicle_no       VARCHAR(20) NOT NULL,
    registration_no  VARCHAR(20) NOT NULL,
    vehicle_type     VARCHAR(10) NOT NULL DEFAULT 'BUS',
    make             VARCHAR(50) NOT NULL,
    model            VARCHAR(50) NOT NULL,
    manufacture_year INT,
    seating_capacity INT         NOT NULL,
    standing_capacity INT,
    fuel_type        VARCHAR(10) NOT NULL DEFAULT 'DIESEL',
    insurance_expiry DATE,
    puc_expiry       DATE,
    status           VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    route_id         BIGINT REFERENCES transport_routes (id) ON DELETE SET NULL,
    driver_id        BIGINT REFERENCES transport_drivers (id) ON DELETE SET NULL,
    remarks          VARCHAR(300)
);
CREATE UNIQUE INDEX uq_transport_vehicle_no ON transport_vehicles (institution_id, lower(vehicle_no));
CREATE UNIQUE INDEX uq_transport_vehicle_reg ON transport_vehicles (institution_id, lower(registration_no));
CREATE UNIQUE INDEX uq_transport_vehicle_driver ON transport_vehicles (driver_id) WHERE driver_id IS NOT NULL;
