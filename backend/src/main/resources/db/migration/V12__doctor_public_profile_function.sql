-- Browsing doctors (department -> doctor -> slot, the patient's first
-- screen) needs each doctor's display name, which lives on app_user —
-- FORCE ROW LEVEL SECURITY, no RlsContext exists for public browsing. Same
-- pattern as V6/V8/V10: expose only what's genuinely public (name,
-- department, bio), never email/CID/DOB/address, via a narrow SECURITY
-- DEFINER function rather than a blanket bypass.
CREATE FUNCTION get_doctor_public_profiles(p_department_id UUID DEFAULT NULL)
RETURNS TABLE (
    doctor_id UUID,
    department_id UUID,
    first_name TEXT,
    last_name TEXT,
    bio TEXT
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT d.id, d.department_id, u.first_name, u.last_name, d.bio
    FROM doctor d
    JOIN app_user u ON u.id = d.user_id
    WHERE p_department_id IS NULL OR d.department_id = p_department_id;
$$;

REVOKE ALL ON FUNCTION get_doctor_public_profiles(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION get_doctor_public_profiles(UUID) TO scheduler_app;
