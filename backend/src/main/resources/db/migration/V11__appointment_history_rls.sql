-- appointment_history carries the same sensitivity as appointment itself
-- (who changed what, when) and was missed in V3's initial RLS pass. Scoped
-- via a join back to appointment rather than its own patient/doctor
-- columns, since history rows don't duplicate that ownership data directly.
ALTER TABLE appointment_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment_history FORCE ROW LEVEL SECURITY;

CREATE POLICY appointment_history_scoped ON appointment_history
    FOR SELECT
    USING (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR EXISTS (
            SELECT 1 FROM appointment a
            WHERE a.id = appointment_history.appointment_id
              AND (
                  (current_setting('app.current_role', true) = 'PATIENT'
                      AND a.patient_id = current_setting('app.current_user_id', true)::uuid)
                  OR (current_setting('app.current_role', true) = 'DOCTOR'
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE user_id = current_setting('app.current_user_id', true)::uuid))
                  OR (current_setting('app.current_role', true) IN ('NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN')
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE department_id = current_setting('app.current_department_id', true)::uuid))
              )
        )
    );

-- Writes happen only from BookingService/status-change services, always
-- inside the same transaction as the appointment mutation they record —
-- INSERT is allowed for any authenticated role; the WITH CHECK mirrors the
-- SELECT policy so a session can only ever write a history row for an
-- appointment it could also see.
CREATE POLICY appointment_history_insert ON appointment_history
    FOR INSERT
    WITH CHECK (
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR EXISTS (
            SELECT 1 FROM appointment a
            WHERE a.id = appointment_history.appointment_id
              AND (
                  (current_setting('app.current_role', true) = 'PATIENT'
                      AND a.patient_id = current_setting('app.current_user_id', true)::uuid)
                  OR (current_setting('app.current_role', true) = 'DOCTOR'
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE user_id = current_setting('app.current_user_id', true)::uuid))
                  OR (current_setting('app.current_role', true) IN ('NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN')
                      AND a.doctor_id IN (SELECT id FROM doctor WHERE department_id = current_setting('app.current_department_id', true)::uuid))
              )
        )
    );
