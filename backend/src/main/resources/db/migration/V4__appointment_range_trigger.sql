-- Hibernate doesn't natively map Postgres's TSTZRANGE type, and the app
-- shouldn't have to hand-construct range literals anyway. Java only ever
-- sets start_time and appointment_type_id; this trigger derives end_time
-- (start_time + the appointment type's duration+buffer) and appointment_range
-- automatically, so there's exactly one place range/end-time logic lives —
-- Java can never write a start/end pair that disagrees with the constraint
-- Postgres actually enforces.

CREATE OR REPLACE FUNCTION compute_appointment_range() RETURNS TRIGGER AS $$
DECLARE
    footprint INTERVAL;
BEGIN
    SELECT (duration_minutes + buffer_minutes) * INTERVAL '1 minute'
        INTO footprint
        FROM appointment_type
        WHERE id = NEW.appointment_type_id;

    IF footprint IS NULL THEN
        RAISE EXCEPTION 'appointment_type % not found', NEW.appointment_type_id;
    END IF;

    NEW.end_time := NEW.start_time + footprint;
    -- '[)' : start inclusive, end exclusive — a slot ending at 10:00 and the
    -- next one starting at 10:00 do not count as overlapping.
    NEW.appointment_range := tstzrange(NEW.start_time, NEW.end_time, '[)');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_compute_appointment_range
    BEFORE INSERT OR UPDATE OF start_time, appointment_type_id ON appointment
    FOR EACH ROW EXECUTE FUNCTION compute_appointment_range();
