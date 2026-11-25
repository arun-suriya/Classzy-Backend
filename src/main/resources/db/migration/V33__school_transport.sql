-- School transport desk. Routes, stops, vehicles and drivers already exist (V9); this adds what the desk needs on top.
ALTER TABLE transport_routes ADD COLUMN trip_type  VARCHAR(8) NOT NULL DEFAULT 'PICKUP';
ALTER TABLE transport_routes ADD COLUMN vehicle_id BIGINT REFERENCES transport_vehicles (id) ON DELETE SET NULL;
ALTER TABLE transport_routes ADD CONSTRAINT ck_route_trip_type CHECK (trip_type IN ('PICKUP', 'DROP'));
CREATE INDEX ix_route_vehicle ON transport_routes (vehicle_id);

CREATE TABLE school_trn_allocations (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    student_id     BIGINT      NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    route_id       BIGINT      NOT NULL REFERENCES transport_routes (id) ON DELETE CASCADE,
    stop_id        BIGINT      NOT NULL REFERENCES transport_stops (id),
    trip_type      VARCHAR(8)  NOT NULL,
    created_by     VARCHAR(200),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trn_alloc UNIQUE (student_id, trip_type)
);
CREATE INDEX ix_trn_alloc_route ON school_trn_allocations (route_id);
CREATE INDEX ix_trn_alloc_stop ON school_trn_allocations (stop_id);

CREATE TABLE school_trn_trips (
    id              BIGSERIAL PRIMARY KEY,
    institution_id  BIGINT      NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    route_id        BIGINT      NOT NULL REFERENCES transport_routes (id) ON DELETE CASCADE,
    trip_date       DATE        NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'SCHEDULED',
    delayed         BOOLEAN     NOT NULL DEFAULT FALSE,
    delay_minutes   INT         NOT NULL DEFAULT 0 CHECK (delay_minutes >= 0),
    current_stop_id BIGINT REFERENCES transport_stops (id) ON DELETE SET NULL,
    updated_by      VARCHAR(200),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trn_trip UNIQUE (route_id, trip_date),
    CONSTRAINT ck_trn_trip_status CHECK (status IN ('SCHEDULED', 'ON_TIME', 'DELAYED', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX ix_trn_trip_day ON school_trn_trips (institution_id, trip_date);

CREATE TABLE school_trn_attendants (
    id             BIGSERIAL PRIMARY KEY,
    institution_id BIGINT       NOT NULL REFERENCES institutions (id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    vehicle_id     BIGINT REFERENCES transport_vehicles (id) ON DELETE SET NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_trn_att_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_trn_att_inst ON school_trn_attendants (institution_id);
