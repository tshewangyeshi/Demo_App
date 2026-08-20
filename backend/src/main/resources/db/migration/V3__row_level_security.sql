-- RLS is the design's DB-level RBAC guarantee. It only binds because
-- V1 created scheduler_app as a NON-OWNER role — table owners bypass RLS
-- by default, which is the exact gap the design doc calls out as the
-- single most important correctness fix in this schema.
--
-- Session variables (set per-request by the app, inside the same
-- transaction, via SET LOCAL — see design doc "RLS session variables and
-- connection pooling"):
--   app.current_user_id        - the authenticated user's id
--   app.current_role           - one of the 7 roles
--   app.current_department_id  - for department-scoped roles (NURSE, DEPARTMENT_ADMIN); '' otherwise

ALTER TABLE appointment ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment FORCE ROW LEVEL SECURITY;

ALTER TABLE doctor_schedule ENABLE ROW LEVEL SECURITY;
ALTER TABLE doctor_schedule FORCE ROW LEVEL SECURITY;

ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_user FORCE ROW LEVEL SECURITY;

-- app_user: everyone can read their own row; hospital/super admin can read all.
CREATE POLICY app_user_self_or_admin ON app_user
    FOR SELECT
    USING (
        id = current_setting('app.current_user_id', true)::uuid
        OR current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
    );

CREATE POLICY app_user_admin_write ON app_user
    FOR ALL
    USING (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN'))
    WITH CHECK (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN'));

-- appointment: patient sees only their own; doctor sees only their own
-- schedule's appointments; nurse/department_admin scoped to their
-- department (via the doctor's department); hospital/super admin see all.
CREATE POLICY appointment_patient_own ON appointment
    FOR ALL
    USING (
        current_setting('app.current_role', true) = 'PATIENT'
        AND patient_id = current_setting('app.current_user_id', true)::uuid
    )
    WITH CHECK (
        current_setting('app.current_role', true) = 'PATIENT'
        AND patient_id = current_setting('app.current_user_id', true)::uuid
    );

CREATE POLICY appointment_doctor_own ON appointment
    FOR ALL
    USING (
        current_setting('app.current_role', true) = 'DOCTOR'
        AND doctor_id IN (
            SELECT id FROM doctor WHERE user_id = current_setting('app.current_user_id', true)::uuid
        )
    );

CREATE POLICY appointment_department_scoped_staff ON appointment
    FOR ALL
    USING (
        current_setting('app.current_role', true) IN ('NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN')
        AND doctor_id IN (
            SELECT id FROM doctor WHERE department_id = current_setting('app.current_department_id', true)::uuid
        )
    );

CREATE POLICY appointment_hospital_admin_all ON appointment
    FOR ALL
    USING (current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN'));

-- doctor_schedule: department-scoped staff + the doctor themselves + admins.
CREATE POLICY doctor_schedule_department_scoped ON doctor_schedule
    FOR SELECT
    USING (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR doctor_id IN (
            SELECT id FROM doctor WHERE department_id = current_setting('app.current_department_id', true)::uuid
        )
        OR doctor_id IN (
            SELECT id FROM doctor WHERE user_id = current_setting('app.current_user_id', true)::uuid
        )
    );

CREATE POLICY doctor_schedule_write_scoped ON doctor_schedule
    FOR INSERT
    WITH CHECK (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN', 'DEPARTMENT_ADMIN')
    );

-- Department/specialty/appointment_type/doctor listings are public reference
-- data (patients browse them pre-login) — deliberately NOT RLS-protected.
-- audit_log and notification_outbox are backend-only, never queried with
-- request-scoped RLS context; access is restricted at the application layer.
