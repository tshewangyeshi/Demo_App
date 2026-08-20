-- The staff daily queue and doctor portal need to show a patient's NAME, not
-- just their id — but app_user's RLS (app_user_self_or_admin) only lets a
-- caller read their own row or, if HOSPITAL_ADMIN/SUPER_ADMIN, every row.
-- A nurse/receptionist/department admin/doctor legitimately needs to see the
-- names of patients booked with doctors they can already see appointments
-- for (via appointment_department_scoped_staff / appointment_doctor_own),
-- but has no RLS path to app_user itself.
--
-- Same shape as V12's get_doctor_public_profiles: a narrow SECURITY DEFINER
-- function that returns ONLY first/last name (no CID, DOB, address, contact
-- info), and whose WHERE clause is a direct copy of the three role branches
-- already in V3's appointment policies — it cannot expose a patient name
-- that appointment RLS wouldn't already let this caller see the appointment
-- for.
CREATE FUNCTION get_visible_patient_names()
RETURNS TABLE (patient_id uuid, first_name text, last_name text)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT DISTINCT u.id, u.first_name, u.last_name
    FROM app_user u
    JOIN appointment a ON a.patient_id = u.id
    WHERE
        current_setting('app.current_role', true) IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        OR (
            current_setting('app.current_role', true) = 'DOCTOR'
            AND a.doctor_id IN (
                SELECT id FROM doctor WHERE user_id = current_setting('app.current_user_id', true)::uuid
            )
        )
        OR (
            current_setting('app.current_role', true) IN ('NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN')
            AND a.doctor_id IN (
                SELECT id FROM doctor WHERE department_id = NULLIF(current_setting('app.current_department_id', true), '')::uuid
            )
        );
$$;

REVOKE ALL ON FUNCTION get_visible_patient_names() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION get_visible_patient_names() TO scheduler_app;
