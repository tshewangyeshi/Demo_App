-- BookingService checks for a reference-number collision BEFORE inserting
-- (see its comment on why a retry-after-failure can't work: Postgres aborts
-- the whole transaction on any statement error). But the caller booking an
-- appointment is always a PATIENT, and appointment's RLS
-- (appointment_patient_own) only lets a patient see their OWN rows — a
-- plain RLS-scoped query here would only ever check the booking patient's
-- own appointments, never catching a collision against a DIFFERENT
-- patient's reference number, which is the whole point of the check. Same
-- narrow SECURITY DEFINER pattern as V6/V10/V12/V15/V18 for exactly this
-- "must see past RLS for one specific, safe purpose" need — this one
-- returns a boolean, nothing else, so it can't leak anything.
CREATE FUNCTION reference_number_active_exists(p_reference_number TEXT)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM appointment
        WHERE reference_number = p_reference_number
          AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'WAITING', 'IN_CONSULTATION')
    );
$$;

REVOKE ALL ON FUNCTION reference_number_active_exists(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reference_number_active_exists(TEXT) TO scheduler_app;
