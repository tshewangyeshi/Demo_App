-- Login must find a user by email BEFORE any RLS context (app.current_role
-- etc.) exists for the request — but app_user has FORCE ROW LEVEL SECURITY,
-- and every existing policy on it requires app.current_user_id/current_role
-- to already be set. Without this, a fresh connection's login lookup would
-- be denied by RLS entirely (current_setting returns null pre-auth, no
-- policy matches).
--
-- Fix: a narrow SECURITY DEFINER function, owned by the schema-owning role,
-- that runs with the OWNER's privileges (bypassing RLS) for exactly this one
-- query — never a blanket RLS policy that would let any unauthenticated
-- session read the whole app_user table. scheduler_app can EXECUTE the
-- function but still cannot SELECT * FROM app_user directly without a valid
-- RlsContext.
CREATE FUNCTION find_user_credentials_by_email(p_email TEXT)
RETURNS TABLE (
    id UUID,
    password_hash TEXT,
    role TEXT,
    department_id UUID,
    account_status TEXT
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT id, password_hash, role, department_id, account_status
    FROM app_user
    WHERE email = p_email;
$$;

REVOKE ALL ON FUNCTION find_user_credentials_by_email(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION find_user_credentials_by_email(TEXT) TO scheduler_app;
