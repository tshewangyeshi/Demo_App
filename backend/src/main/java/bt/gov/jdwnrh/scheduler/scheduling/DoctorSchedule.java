package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A doctor's recurring weekly working hours. Actual availability on a given
 * date is this MINUS {@link ScheduleException}s (leave/blocked periods),
 * {@link bt.gov.jdwnrh.scheduler.department.Department} holidays, and
 * existing appointments — computed by SlotGenerationService, never by the
 * frontend.
 */
@Entity
@Table(name = "doctor_schedule")
public class DoctorSchedule {

    @Id
    private UUID id;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    /** 0=Sunday .. 6=Saturday, matching the V2 migration's CHECK constraint. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeekValue;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DoctorSchedule() {
        // JPA
    }

    public DoctorSchedule(UUID id, UUID doctorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.id = id;
        this.doctorId = doctorId;
        this.dayOfWeekValue = dayOfWeek.getValue() % 7; // java DayOfWeek: Mon=1..Sun=7 -> our 0=Sun..6=Sat
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public DayOfWeek getDayOfWeek() {
        int isoValue = dayOfWeekValue == 0 ? 7 : dayOfWeekValue; // back to java's Mon=1..Sun=7
        return DayOfWeek.of(isoValue);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
