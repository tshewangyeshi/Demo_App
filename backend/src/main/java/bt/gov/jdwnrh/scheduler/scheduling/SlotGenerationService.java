package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import bt.gov.jdwnrh.scheduler.department.AppointmentType;

/**
 * Pure(ish) domain logic — reads DoctorSchedule/exceptions/holidays/existing
 * bookings and computes available slots. The frontend is NEVER the source of
 * truth for availability (see docs/designs/jdwnrh-scheduler.md,
 * "Data & Correctness Architecture") — this is that source of truth.
 *
 * Working hours are stored as LocalTime, implicitly in Asia/Thimphu (the
 * hospital's timezone) — this service is where that local time gets
 * combined with a calendar date and converted to UTC Instants, which is what
 * the rest of the system (Appointment.startTime, the exclusion constraint)
 * operates on.
 */
@Service
public class SlotGenerationService {

    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Thimphu");

    private final DoctorScheduleRepository doctorScheduleRepository;
    private final ScheduleExceptionRepository scheduleExceptionRepository;
    private final HolidayRepository holidayRepository;
    private final DoctorBusyRangesRepository doctorBusyRangesRepository;
    private final Clock clock;

    public SlotGenerationService(DoctorScheduleRepository doctorScheduleRepository,
                                  ScheduleExceptionRepository scheduleExceptionRepository,
                                  HolidayRepository holidayRepository,
                                  DoctorBusyRangesRepository doctorBusyRangesRepository,
                                  Clock clock) {
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.scheduleExceptionRepository = scheduleExceptionRepository;
        this.holidayRepository = holidayRepository;
        this.doctorBusyRangesRepository = doctorBusyRangesRepository;
        this.clock = clock;
    }

    public List<Slot> generateSlots(UUID doctorId, UUID departmentId, LocalDate date, AppointmentType appointmentType) {
        if (!holidayRepository.findApplicable(date, departmentId).isEmpty()) {
            return List.of(); // hospital/department closed — no slots at all, regardless of doctor's normal schedule
        }

        List<DoctorSchedule> daySchedules = doctorScheduleRepository.findByDoctorId(doctorId).stream()
                .filter(s -> s.getDayOfWeek() == date.getDayOfWeek())
                .toList();

        if (daySchedules.isEmpty()) {
            return List.of();
        }

        Instant dayStart = date.atStartOfDay(HOSPITAL_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(HOSPITAL_ZONE).toInstant();

        List<ScheduleException> exceptions = scheduleExceptionRepository.findOverlapping(doctorId, dayStart, dayEnd);
        List<DoctorBusyRangesRepository.BusyRange> busyRanges =
                doctorBusyRangesRepository.findBusyRanges(doctorId, dayStart, dayEnd);

        Instant now = clock.instant();

        List<Slot> slots = new ArrayList<>();
        for (DoctorSchedule schedule : daySchedules) {
            ZonedDateTime windowStart = date.atTime(schedule.getStartTime()).atZone(HOSPITAL_ZONE);
            ZonedDateTime windowEnd = date.atTime(schedule.getEndTime()).atZone(HOSPITAL_ZONE);

            Instant cursor = windowStart.toInstant();
            Instant windowEndInstant = windowEnd.toInstant();
            Instant step = cursor.plus(appointmentType.slotFootprint());

            while (!step.isAfter(windowEndInstant)) {
                Instant candidateStart = cursor;
                Instant candidateEnd = step;

                boolean inPast = candidateStart.isBefore(now);
                boolean blockedByException = exceptions.stream()
                        .anyMatch(ex -> ex.start().isBefore(candidateEnd) && candidateStart.isBefore(ex.end()));
                boolean blockedByBooking = busyRanges.stream()
                        .anyMatch(busy -> busy.overlaps(candidateStart, candidateEnd));

                if (!inPast && !blockedByException && !blockedByBooking) {
                    slots.add(new Slot(candidateStart, candidateEnd));
                }

                cursor = step;
                step = cursor.plus(appointmentType.slotFootprint());
            }
        }

        return slots;
    }
}
