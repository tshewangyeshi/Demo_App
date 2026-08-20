package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.util.UUID;

/** Used by staff/doctor daily views — the patient's own /mine view doesn't need its own name attached. */
public record AppointmentWithPatientResponse(
        UUID id,
        String referenceNumber,
        UUID doctorId,
        UUID patientId,
        String patientName,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status) {

    public static AppointmentWithPatientResponse from(Appointment appointment, String patientName) {
        return new AppointmentWithPatientResponse(
                appointment.getId(),
                appointment.getReferenceNumber(),
                appointment.getDoctorId(),
                appointment.getPatientId(),
                patientName,
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus());
    }
}
