package bt.gov.jdwnrh.scheduler.appointment;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;

/** RLS does the actual scoping: findAll() here only ever returns rows the caller's role/ownership permits. */
@Service
public class AppointmentQueryService {

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
}
