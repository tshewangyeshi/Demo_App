package bt.gov.jdwnrh.scheduler.appointment;

/**
 * Thrown for both "doesn't exist" and "exists but RLS hides it from you" —
 * deliberately the same message either way, so a cancellation attempt can't
 * be used to probe whether a given appointment ID belongs to someone else.
 */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(String message) {
        super(message);
    }
}
