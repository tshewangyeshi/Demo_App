package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Component;

import bt.gov.jdwnrh.scheduler.department.AppointmentType;
import bt.gov.jdwnrh.scheduler.scheduling.SlotGenerationService;

/**
 * Shared by BookingService (new bookings) and AppointmentLifecycleService
 * (reschedule's new slot) — the exclusion constraint alone doesn't know
 * about working hours/exceptions/holidays, so both paths re-check against
 * SlotGenerationService before attempting the write.
 */
@Component
public class SlotAvailabilityChecker {

    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Thimphu");

    private final SlotGenerationService slotGenerationService;

    public SlotAvailabilityChecker(SlotGenerationService slotGenerationService) {
        this.slotGenerationService = slotGenerationService;
    }

    public boolean isAvailable(UUID doctorId, UUID departmentId, AppointmentType appointmentType, Instant start) {
        Instant end = start.plus(appointmentType.slotFootprint());
        return slotGenerationService
                .generateSlots(doctorId, departmentId, start.atZone(HOSPITAL_ZONE).toLocalDate(), appointmentType)
                .stream()
                .anyMatch(slot -> slot.start().equals(start) && slot.end().equals(end));
    }
}
