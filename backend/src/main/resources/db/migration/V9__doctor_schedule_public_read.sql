-- V3's doctor_schedule SELECT policy only covers department-scoped staff,
-- the doctor themselves, and admins — but ANY patient browsing for an
-- appointment needs to read ANY doctor's weekly working hours to see
-- available slots. A recurring weekly schedule template ("Dr. X works
-- Mon/Wed 9-12") is not sensitive the way an individual Appointment row is
-- (who booked what, when) — it's the same kind of public reference data as
-- department/specialty listings.
--
-- Postgres OR's multiple permissive policies for the same command together,
-- so this makes SELECT effectively public while leaving V3's INSERT policy
-- (admin/department-admin only) as the sole gate on writes.
CREATE POLICY doctor_schedule_public_read ON doctor_schedule
    FOR SELECT
    USING (true);
