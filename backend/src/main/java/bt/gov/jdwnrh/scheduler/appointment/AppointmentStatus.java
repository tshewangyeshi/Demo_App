package bt.gov.jdwnrh.scheduler.appointment;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * See docs/designs/jdwnrh-scheduler.md, "Appointment Status Model". Transitions
 * are controlled — never write a status directly without going through
 * {@link #canTransitionTo(AppointmentStatus)}.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    WAITING,
    IN_CONSULTATION,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    RESCHEDULED;

    /** Statuses that occupy a slot — matches the DB exclusion constraint's WHERE clause exactly. */
    public static final Set<AppointmentStatus> ACTIVE = EnumSet.of(
            PENDING, CONFIRMED, CHECKED_IN, WAITING, IN_CONSULTATION);

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(AppointmentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING, EnumSet.of(CONFIRMED, CANCELLED, RESCHEDULED));
        ALLOWED_TRANSITIONS.put(CONFIRMED, EnumSet.of(CHECKED_IN, CANCELLED, RESCHEDULED, NO_SHOW));
        ALLOWED_TRANSITIONS.put(CHECKED_IN, EnumSet.of(WAITING, CANCELLED));
        ALLOWED_TRANSITIONS.put(WAITING, EnumSet.of(IN_CONSULTATION, CANCELLED));
        ALLOWED_TRANSITIONS.put(IN_CONSULTATION, EnumSet.of(COMPLETED));
        ALLOWED_TRANSITIONS.put(COMPLETED, EnumSet.noneOf(AppointmentStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(AppointmentStatus.class));
        ALLOWED_TRANSITIONS.put(NO_SHOW, EnumSet.noneOf(AppointmentStatus.class));
        ALLOWED_TRANSITIONS.put(RESCHEDULED, EnumSet.noneOf(AppointmentStatus.class));
    }

    public boolean canTransitionTo(AppointmentStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
