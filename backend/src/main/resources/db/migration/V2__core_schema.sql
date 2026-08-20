-- Core schema for the walking-skeleton milestone (patient role, one
-- department, full booking correctness). See docs/designs/jdwnrh-scheduler.md.

CREATE TABLE app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL UNIQUE,
    mobile_number   TEXT,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN
                        ('PATIENT','DOCTOR','NURSE','RECEPTIONIST',
                         'DEPARTMENT_ADMIN','HOSPITAL_ADMIN','SUPER_ADMIN')),
    department_id   UUID, -- FK added after department table exists; NULL for PATIENT/HOSPITAL_ADMIN/SUPER_ADMIN
    cid_number      TEXT,
    first_name      TEXT NOT NULL,
    middle_name     TEXT,
    last_name       TEXT NOT NULL,
    date_of_birth   DATE,
    gender          TEXT,
    dzongkhag       TEXT,
    gewog           TEXT,
    village         TEXT,
    account_status  TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE','SUSPENDED','DEACTIVATED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE department (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_department FOREIGN KEY (department_id) REFERENCES department(id);

CREATE TABLE specialty (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id   UUID NOT NULL REFERENCES department(id),
    name            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (department_id, name)
);

CREATE TABLE appointment_type (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    specialty_id        UUID NOT NULL REFERENCES specialty(id),
    name                TEXT NOT NULL,
    duration_minutes    INT NOT NULL CHECK (duration_minutes > 0),
    buffer_minutes      INT NOT NULL DEFAULT 0 CHECK (buffer_minutes >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE REFERENCES app_user(id),
    department_id   UUID NOT NULL REFERENCES department(id),
    bio             TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_schedule (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL REFERENCES doctor(id),
    day_of_week     INT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6), -- 0=Sunday
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL CHECK (end_time > start_time),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE schedule_exception (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL REFERENCES doctor(id),
    exception_range TSTZRANGE NOT NULL, -- leave, training, blocked period, etc. — always UTC
    reason          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE holiday (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department_id   UUID REFERENCES department(id), -- NULL = hospital-wide
    holiday_date    DATE NOT NULL,
    name            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Appointment is the load-bearing table: all timestamps stored UTC, rendered
-- Asia/Thimphu at the presentation layer only (see design doc "Decisions
-- Resolved During Review" -> Timezone handling).
CREATE TABLE appointment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number    TEXT NOT NULL, -- deliberately NOT globally unique — see partial index below
    patient_id          UUID NOT NULL REFERENCES app_user(id),
    doctor_id           UUID NOT NULL REFERENCES doctor(id),
    appointment_type_id UUID NOT NULL REFERENCES appointment_type(id),
    start_time          TIMESTAMPTZ NOT NULL, -- what Java code reads/writes; UTC
    end_time            TIMESTAMPTZ NOT NULL, -- start_time + appointment_type's (duration + buffer) — maintained by trigger, see V4
    appointment_range   TSTZRANGE NOT NULL,   -- derived from start_time/end_time by trigger; only Postgres (the exclusion constraint) reads this directly
    status              TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN
                            ('PENDING','CONFIRMED','CHECKED_IN','WAITING',
                             'IN_CONSULTATION','COMPLETED','CANCELLED',
                             'NO_SHOW','RESCHEDULED')),
    rescheduled_from_id UUID REFERENCES appointment(id),
    reminded_24h        BOOLEAN NOT NULL DEFAULT false,
    reminded_2h         BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- The mechanism the whole design's correctness claim rests on: no two
    -- ACTIVE (occupying) appointments may overlap for the same doctor.
    -- Partial (WHERE clause) so cancelled/no-show/rescheduled/completed rows
    -- never block rebooking — see design doc "buffer time is enforced
    -- inside the exclusion constraint" and "must be partial, scoped to
    -- active statuses".
    CONSTRAINT no_overlapping_active_appointments
        EXCLUDE USING gist (
            doctor_id WITH =,
            appointment_range WITH &&
        ) WHERE (status IN ('PENDING','CONFIRMED','CHECKED_IN','WAITING','IN_CONSULTATION'))
);

-- Reference-number lookup must resolve to the current ACTIVE appointment
-- even though a reschedule chain can leave multiple historical rows sharing
-- one reference number — see design doc "reference_number is not a plain-
-- unique column".
CREATE UNIQUE INDEX uq_appointment_reference_active
    ON appointment (reference_number)
    WHERE (status IN ('PENDING','CONFIRMED','CHECKED_IN','WAITING','IN_CONSULTATION'));

CREATE INDEX idx_appointment_patient ON appointment (patient_id);
CREATE INDEX idx_appointment_doctor ON appointment (doctor_id);

CREATE TABLE appointment_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id  UUID NOT NULL REFERENCES appointment(id),
    from_status     TEXT,
    to_status       TEXT NOT NULL,
    changed_by      UUID NOT NULL REFERENCES app_user(id),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    note            TEXT
);

CREATE TABLE notification_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id  UUID REFERENCES appointment(id),
    event_type      TEXT NOT NULL, -- BOOKING_CONFIRMED, REMINDER_24H, REMINDER_2H, CANCELLED, RESCHEDULED
    recipient_email TEXT NOT NULL,
    payload         JSONB NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    attempts        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_notification_outbox_pending ON notification_outbox (status) WHERE status = 'PENDING';

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID REFERENCES app_user(id),
    action          TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     UUID,
    previous_value  JSONB,
    new_value       JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_resource ON audit_log (resource_type, resource_id);
