package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        String referenceNumber,
        UUID doctorId,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getReferenceNumber(),
                appointment.getDoctorId(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus());
    }
}
