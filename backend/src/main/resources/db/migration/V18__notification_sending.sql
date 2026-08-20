-- Closes the gap the design doc calls out explicitly: booking/cancel/
-- reschedule already write a notification_outbox row (see NotificationEnqueuer),
-- but nothing has ever consumed it. This migration adds what
-- NotificationSenderJob and ReminderJob need.

-- Retry/backoff for the outbox sender: a row becomes eligible again once
-- next_attempt_at passes, not on every single poll — without this, a
-- transient provider failure would get hammered every poll cycle instead of
-- backing off.
ALTER TABLE notification_outbox ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Partial index matching the sender job's claim query exactly (status='PENDING' AND next_attempt_at <= now()).
CREATE INDEX idx_notification_outbox_ready ON notification_outbox (next_attempt_at) WHERE status = 'PENDING';

-- The reminder job has no authenticated caller (it's a background job, no
-- RlsContext, no session vars) — appointment and app_user both carry FORCE
-- ROW LEVEL SECURITY, so a plain query from it would silently see zero rows.
-- Same "narrow SECURITY DEFINER bypass" pattern as V6/V10/V12/V15, but this
-- pair also DOES the claim atomically: the design doc requires exclusive
-- claiming (see "The reminder job needs a distributed lock, not a plain
-- @Scheduled poller") because more than one backend instance can be running
-- during a deploy overlap — UPDATE ... WHERE id IN (SELECT ... FOR UPDATE
-- SKIP LOCKED) is the standard Postgres claim-a-job-queue-row pattern: only
-- one instance can ever win a given row, and the transaction wrapping the
-- whole claim+enqueue means a crash mid-processing rolls the claim back too
-- (it'll just be picked up again next poll), never silently drops a reminder.
CREATE FUNCTION claim_24h_appointment_reminders(p_batch_size INT)
RETURNS TABLE (appointment_id UUID, doctor_id UUID, start_time TIMESTAMPTZ, reference_number TEXT, recipient_email TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    WITH claimed AS (
        UPDATE appointment
        SET reminded_24h = true
        WHERE id IN (
            SELECT id FROM appointment
            WHERE status = 'CONFIRMED'
              AND start_time BETWEEN now() AND now() + INTERVAL '24 hours'
              AND NOT reminded_24h
            ORDER BY start_time
            LIMIT p_batch_size
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id, doctor_id, start_time, reference_number, patient_id
    )
    SELECT c.id, c.doctor_id, c.start_time, c.reference_number, u.email
    FROM claimed c
    JOIN app_user u ON u.id = c.patient_id;
$$;

CREATE FUNCTION claim_2h_appointment_reminders(p_batch_size INT)
RETURNS TABLE (appointment_id UUID, doctor_id UUID, start_time TIMESTAMPTZ, reference_number TEXT, recipient_email TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    WITH claimed AS (
        UPDATE appointment
        SET reminded_2h = true
        WHERE id IN (
            SELECT id FROM appointment
            WHERE status = 'CONFIRMED'
              AND start_time BETWEEN now() AND now() + INTERVAL '2 hours'
              AND NOT reminded_2h
            ORDER BY start_time
            LIMIT p_batch_size
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id, doctor_id, start_time, reference_number, patient_id
    )
    SELECT c.id, c.doctor_id, c.start_time, c.reference_number, u.email
    FROM claimed c
    JOIN app_user u ON u.id = c.patient_id;
$$;

REVOKE ALL ON FUNCTION claim_24h_appointment_reminders(INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION claim_2h_appointment_reminders(INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION claim_24h_appointment_reminders(INT) TO scheduler_app;
GRANT EXECUTE ON FUNCTION claim_2h_appointment_reminders(INT) TO scheduler_app;
