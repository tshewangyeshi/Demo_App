package bt.gov.jdwnrh.scheduler.department;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointment_type")
public class AppointmentType {

    @Id
    private UUID id;

    @Column(name = "specialty_id", nullable = false)
    private UUID specialtyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppointmentType() {
        // JPA
    }

    public AppointmentType(UUID id, UUID specialtyId, String name, int durationMinutes, int bufferMinutes, Instant now) {
        this.id = id;
        this.specialtyId = specialtyId;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSpecialtyId() {
        return specialtyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public void setBufferMinutes(int bufferMinutes) {
        this.bufferMinutes = bufferMinutes;
    }

    public Duration duration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public Duration buffer() {
        return Duration.ofMinutes(bufferMinutes);
    }

    /** The full range an appointment of this type occupies on a doctor's calendar, including buffer. */
    public Duration slotFootprint() {
        return duration().plus(buffer());
    }
}
