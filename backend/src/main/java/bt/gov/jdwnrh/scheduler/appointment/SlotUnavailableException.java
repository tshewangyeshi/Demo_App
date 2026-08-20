package bt.gov.jdwnrh.scheduler.appointment;

/**
 * Thrown when a requested slot is not actually bookable — either because it
 * falls outside working hours/is blocked by an exception or holiday (caught
 * before the INSERT, by re-checking against SlotGenerationService), or
 * because a concurrent booking won the race and the exclusion constraint
 * rejected the INSERT (caught after). Either way, the caller sees a clean
 * "pick another time" message, never a raw 409/500 — see design doc,
 * Interaction States table.
 */
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(String message) {
        super(message);
    }
}
