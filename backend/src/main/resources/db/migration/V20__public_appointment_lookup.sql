-- See docs/designs/jdwnrh-scheduler.md, "Scope Expansion" -> E2: public
-- appointment-reference lookup, no login. Requires reference number PLUS a
-- second identifier (last name here) to resolve — a reference number alone
-- isn't secret enough to gate access to an appointment's details, but
-- reference+lastname together makes blind enumeration impractical (paired
-- with the app-layer rate limiter — see InMemoryRateLimiter).
--
-- Same narrow SECURITY DEFINER pattern as V6/V10/V12/V15/V18/V19: there's no
-- RlsContext at all for an unauthenticated public request, so a plain query
-- against appointment/app_user (both FORCE RLS) would see nothing. This
-- exposes only reference number, status, start time, and the doctor's
-- name — never the patient's own PII beyond confirming the last name they
-- already provided, never any other patient's data.
CREATE FUNCTION lookup_appointment_by_reference(p_reference_number TEXT, p_last_name TEXT)
RETURNS TABLE (reference_number TEXT, status TEXT, start_time TIMESTAMPTZ, doctor_first_name TEXT, doctor_last_name TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT a.reference_number, a.status, a.start_time, du.first_name, du.last_name
    FROM appointment a
    JOIN app_user u ON u.id = a.patient_id
    JOIN doctor d ON d.id = a.doctor_id
    JOIN app_user du ON du.id = d.user_id
    WHERE a.reference_number = p_reference_number
      AND lower(u.last_name) = lower(p_last_name)
    ORDER BY a.created_at DESC
    LIMIT 1;
$$;

REVOKE ALL ON FUNCTION lookup_appointment_by_reference(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lookup_appointment_by_reference(TEXT, TEXT) TO scheduler_app;
