-- Slot generation needs to know which time ranges are already booked for a
-- doctor to compute real availability — but appointment's RLS policies
-- scope a PATIENT session to only their OWN rows (correctly, for privacy).
-- If slot generation ran under a patient's RlsContext, it would see none of
-- OTHER patients' bookings and incorrectly show already-booked slots as
-- available — the exclusion constraint would still prevent an actual
-- double-booking at INSERT time, but the patient would hit a confusing
-- "that slot was just taken" error on a slot the app just told them was free.
--
-- Fix: expose ONLY the busy/free ranges (no patient identity, no
-- appointment_type, nothing else) via a narrow SECURITY DEFINER function —
-- safe to call from any authenticated session, since "this doctor is busy
-- 10:00-10:30" reveals nothing about who booked it.
CREATE FUNCTION get_doctor_busy_ranges(p_doctor_id UUID, p_from TIMESTAMPTZ, p_to TIMESTAMPTZ)
RETURNS TABLE (
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT start_time, end_time
    FROM appointment
    WHERE doctor_id = p_doctor_id
      AND status IN ('PENDING','CONFIRMED','CHECKED_IN','WAITING','IN_CONSULTATION')
      AND appointment_range && tstzrange(p_from, p_to, '[)');
$$;

REVOKE ALL ON FUNCTION get_doctor_busy_ranges(UUID, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION get_doctor_busy_ranges(UUID, TIMESTAMPTZ, TIMESTAMPTZ) TO scheduler_app;
