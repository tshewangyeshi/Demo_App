package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "holiday")
public class Holiday {

    @Id
    private UUID id;

    /** NULL = hospital-wide holiday, applies to every department. */
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Holiday() {
        // JPA
    }

    public Holiday(UUID id, UUID departmentId, LocalDate holidayDate, String name, Instant now) {
        this.id = id;
        this.departmentId = departmentId;
        this.holidayDate = holidayDate;
        this.name = name;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getName() {
        return name;
    }
}
