package bt.gov.jdwnrh.scheduler.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;

/** RLS does the actual scoping: findAll()/findByStartTime... here only ever return rows the caller's role/ownership permits. */
@Service
public class AppointmentQueryService {

    // Matches SlotGenerationService's HOSPITAL_ZONE — a "day" for staff/doctor daily views is a Bhutan calendar day, not a UTC one.
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Thimphu");

    private final RlsSessionInitializer rlsSessionInitializer;
    private final CurrentUser currentUser;
    private final AppointmentRepository appointmentRepository;

    public AppointmentQueryService(RlsSessionInitializer rlsSessionInitializer, CurrentUser currentUser,
                                    AppointmentRepository appointmentRepository) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.currentUser = currentUser;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Appointment> listVisible() {
        rlsSessionInitializer.applyCurrentContext(currentUser.require());
        return appointmentRepository.findAll(Sort.by(Sort.Direction.DESC, "startTime"));
    }

    /** The staff daily queue / doctor's own daily agenda — same RLS scoping as listVisible(), narrowed to one calendar day. */
    @Transactional(readOnly = true)
    public List<Appointment> listForDay(LocalDate date) {
        rlsSessionInitializer.applyCurrentContext(currentUser.require());
        Instant dayStart = date.atStartOfDay(HOSPITAL_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(HOSPITAL_ZONE).toInstant();
        return appointmentRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(
                dayStart, dayEnd, Sort.by(Sort.Direction.ASC, "startTime"));
    }
}
