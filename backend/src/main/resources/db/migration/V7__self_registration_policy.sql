-- V3's app_user policies only allow INSERT via app_user_admin_write (an
-- authenticated HOSPITAL_ADMIN/SUPER_ADMIN session). Patient self-
-- registration has no session at all yet — there is no app.current_role to
-- check, since the row being inserted IS the identity being created.
--
-- Scoped narrowly to role = 'PATIENT': staff, doctors, and admin accounts
-- are provisioned by an administrator (via app_user_admin_write), never
-- self-registered — matches the design doc's role model (nurse/receptionist/
-- doctor/admin accounts are hospital-issued, not public signup).
CREATE POLICY app_user_self_register ON app_user
    FOR INSERT
    WITH CHECK (role = 'PATIENT');
