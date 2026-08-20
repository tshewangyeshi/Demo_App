package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointment_history")
public class AppointmentHistory {

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private AppointmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private AppointmentStatus toStatus;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    private String note;

    protected AppointmentHistory() {
        // JPA
    }

    public AppointmentHistory(UUID id, UUID appointmentId, AppointmentStatus fromStatus,
                               AppointmentStatus toStatus, UUID changedBy, Instant changedAt, String note) {
        this.id = id;
        this.appointmentId = appointmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.note = note;
    }
}
