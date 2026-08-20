package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor")
public class Doctor {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    private String bio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Doctor() {
        // JPA
    }

    public Doctor(UUID id, UUID userId, UUID departmentId, String bio, Instant now) {
        this.id = id;
        this.userId = userId;
        this.departmentId = departmentId;
        this.bio = bio;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
