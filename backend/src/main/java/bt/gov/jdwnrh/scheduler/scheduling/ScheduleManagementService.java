package bt.gov.jdwnrh.scheduler.scheduling;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;

/**
 * Weekly schedule blocks and leave/blocked-period exceptions. Shared between
 * admin's per-doctor management and the doctor's own portal — the DB is what
 * actually decides who can write what (see V3/V9/V14): admins can touch any
 * doctor's schedule, a DOCTOR-role caller can only touch their own (enforced
 * by schedule_exception_doctor_own / doctor_schedule's admin-only INSERT-
 * UPDATE-DELETE policies), so this service doesn't need to re-derive that
 * distinction itself.
 */
@Service
public class ScheduleManagementService {

    private final RlsSessionInitializer rlsSessionInitializer;
    private final CurrentUser currentUser;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final ScheduleExceptionRepository scheduleExceptionRepository;
    private final Clock clock;

    public ScheduleManagementService(RlsSessionInitializer rlsSessionInitializer, CurrentUser currentUser,
                                      DoctorScheduleRepository doctorScheduleRepository,
                                      ScheduleExceptionRepository scheduleExceptionRepository, Clock clock) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.currentUser = currentUser;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.scheduleExceptionRepository = scheduleExceptionRepository;
        this.clock = clock;
    }

    @Transactional
    public DoctorSchedule addScheduleBlock(UUID doctorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        rlsSessionInitializer.applyCurrentContext(currentUser.require());
        return doctorScheduleRepository.save(
                new DoctorSchedule(UUID.randomUUID(), doctorId, dayOfWeek, startTime, endTime, clock.instant()));
    }

    @Transactional
    public void removeScheduleBlock(UUID scheduleId) {
        rlsSessionInitializer.applyCurrentContext(currentUser.require());
        doctorScheduleRepository.deleteById(scheduleId); // RLS-gated: a no-op if the caller isn't allowed to touch this row
    }

    @Transactional(readOnly = true)
    public List<DoctorSchedule> listScheduleBlocks(UUID doctorId) {
        return doctorScheduleRepository.findByDoctorId(doctorId); // public read, see V9
    }

    @Transactional
    public ScheduleException addException(UUID doctorId, Instant start, Instant end, String reason) {
        rlsSessionInitializer.applyCurrentContext(currentUser.require());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        scheduleExceptionRepository.insert(id, doctorId, start, end, reason, now);
        return new ScheduleException(id, doctorId, start, end, reason);
    }

    @Transactional
    public void removeException(UUID exceptionId) {
        rlsSessionInitializer.applyCurrentContext(currentUser.require());
        scheduleExceptionRepository.deleteById(exceptionId); // RLS-gated: 0 rows affected if the caller isn't allowed to touch this row
    }

    @Transactional(readOnly = true)
    public List<ScheduleException> listExceptions(UUID doctorId) {
        return scheduleExceptionRepository.findByDoctorId(doctorId); // public read, see V14
    }
}
