package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The load-bearing entity. Java only ever sets {@code startTime} and
 * {@code appointmentTypeId} for the time dimension — {@code endTime} and the
 * DB-only {@code appointment_range} column are derived by a trigger (see
 * V4__appointment_range_trigger.sql) from the appointment type's
 * duration+buffer, so there is exactly one place that math happens.
 *
 * Double-booking is prevented by a Postgres partial exclusion constraint on
 * (doctor_id, appointment_range) WHERE status IN (...ACTIVE...) — not by
 * application logic. This entity does not and should not try to re-implement
 * that check; it exists so the constraint violation has somewhere to surface
 * as a clean exception (see BookingService).
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    private UUID id;

    @Column(name = "reference_number", nullable = false)
    private String referenceNumber;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "appointment_type_id", nullable = false)
    private UUID appointmentTypeId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /** Derived by the DB trigger from startTime + appointment type footprint — never set from Java. */
    @Column(name = "end_time", insertable = false, updatable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "rescheduled_from_id")
    private UUID rescheduledFromId;

    @Column(name = "reminded_24h", nullable = false)
    private boolean reminded24h = false;

    @Column(name = "reminded_2h", nullable = false)
    private boolean reminded2h = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
        // JPA
    }

    public Appointment(UUID id, String referenceNumber, UUID patientId, UUID doctorId,
                        UUID appointmentTypeId, Instant startTime) {
        this.id = id;
        this.referenceNumber = referenceNumber;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.appointmentTypeId = appointmentTypeId;
        this.startTime = startTime;
    }

    /**
     * Applies a status transition, enforcing the state machine — throws
     * rather than silently writing an impossible transition.
     */
    public void transitionTo(AppointmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition appointment " + id + " from " + status + " to " + target);
        }
        this.status = target;
    }

    public UUID getId() {
        return id;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public UUID getAppointmentTypeId() {
        return appointmentTypeId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public UUID getRescheduledFromId() {
        return rescheduledFromId;
    }

    public void setRescheduledFromId(UUID rescheduledFromId) {
        this.rescheduledFromId = rescheduledFromId;
    }
}
