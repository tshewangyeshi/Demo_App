-- Bug: app.current_department_id is set to '' (empty string) for roles
-- that aren't department-scoped (PATIENT, DOCTOR, HOSPITAL_ADMIN, SUPER_ADMIN
-- — see RlsSessionInitializer). Casting '' directly to uuid always throws
-- "invalid input syntax for type uuid", and SQL does NOT reliably
-- short-circuit AND/OR — Postgres can evaluate the ::uuid cast even when a
-- sibling condition (role check) would have excluded the row, and RLS
-- policies are evaluated as independent OR'd expressions regardless of
-- order. This broke every read of `appointment` for a PATIENT (department-
-- scoped policies threw before role-scoped ones got a chance to matter).
--
-- Fix: NULLIF(..., '') converts the empty string to NULL before casting —
-- casting NULL is always safe and simply yields NULL, which then correctly
-- fails the `department_id = NULL` comparison (no match) instead of
-- throwing.
ALTER POLICY appointment_department_scoped_staff ON appointment
    USING (
        current_setting('app.current_role', true) IN ('NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN')
        AND doctor_id IN (
            SELECT id FROM doctor WHERE department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid
        )
    );

ALTER POLICY doctor_schedule_department_scoped ON doctor_schedule
    USING (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR doctor_id IN (
            SELECT id FROM doctor WHERE department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid
        )
        OR doctor_id IN (
            SELECT id FROM doctor WHERE user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        )
    );

ALTER POLICY appointment_history_scoped ON appointment_history
    USING (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR EXISTS (
            SELECT 1 FROM appointment a
            WHERE a.id = appointment_history.appointment_id
              AND (
                  (current_setting('app.current_role', true) = 'PATIENT'
                      AND a.patient_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid)
                  OR (current_setting('app.current_role', true) = 'DOCTOR'
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid))
                  OR (current_setting('app.current_role', true) IN ('NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN')
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid))
              )
        )
    );

ALTER POLICY appointment_history_insert ON appointment_history
    WITH CHECK (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR EXISTS (
            SELECT 1 FROM appointment a
            WHERE a.id = appointment_history.appointment_id
              AND (
                  (current_setting('app.current_role', true) = 'PATIENT'
                      AND a.patient_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid)
                  OR (current_setting('app.current_role', true) = 'DOCTOR'
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid))
                  OR (current_setting('app.current_role', true) IN ('NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN')
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid))
              )
        )
    );

-- Also guard the patient/doctor-scoped comparisons on `appointment` itself —
-- current_user_id is always set for an authenticated session, but the same
-- NULLIF-before-cast discipline is worth applying uniformly rather than
-- leaving one cast style un-guarded next to guarded ones.
ALTER POLICY appointment_patient_own ON appointment
    USING (
        current_setting('app.current_role', true) = 'PATIENT'
        AND patient_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
    )
    WITH CHECK (
        current_setting('app.current_role', true) = 'PATIENT'
        AND patient_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
    );

ALTER POLICY appointment_doctor_own ON appointment
    USING (
        current_setting('app.current_role', true) = 'DOCTOR'
        AND doctor_id IN (
            SELECT id FROM doctor WHERE user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        )
    );

ALTER POLICY app_user_self_or_admin ON app_user
    USING (
        id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
        OR current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
    );
