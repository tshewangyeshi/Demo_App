-- Per-request role/department re-check (see design doc, "Role/department
-- change is enforced immediately, not just bounded by token expiry"): the
-- JWT auth filter looks up the CURRENT role/department for every request,
-- rather than trusting the token's embedded claims, so a demoted or
-- reassigned user loses access on their very next request instead of up to
-- 15 minutes later. Same SECURITY DEFINER pattern as the login lookup, keyed
-- by user id instead of email.
CREATE FUNCTION get_current_role_and_department(p_user_id UUID)
RETURNS TABLE (
    role TEXT,
    department_id UUID,
    account_status TEXT
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT role, department_id, account_status
    FROM app_user
    WHERE id = p_user_id;
$$;

REVOKE ALL ON FUNCTION get_current_role_and_department(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION get_current_role_and_department(UUID) TO scheduler_app;
