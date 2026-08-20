package bt.gov.jdwnrh.scheduler.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;
import bt.gov.jdwnrh.scheduler.iam.AppUser;
import bt.gov.jdwnrh.scheduler.iam.AppUserRepository;
import bt.gov.jdwnrh.scheduler.iam.Role;
import bt.gov.jdwnrh.scheduler.scheduling.Doctor;
import bt.gov.jdwnrh.scheduler.scheduling.DoctorRepository;

/**
 * The DB-touching half of AdminUserController, split out so applyCurrentContext
 * (Propagation.MANDATORY — see RlsSessionInitializer) always runs inside a
 * real transaction, same as BookingService/AppointmentLifecycleService.
 */
@Service
public class AdminUserService {

    private final RlsSessionInitializer rlsSessionInitializer;
    private final AppUserRepository appUserRepository;
    private final DoctorRepository doctorRepository;
    private final Clock clock;

    public AdminUserService(RlsSessionInitializer rlsSessionInitializer, AppUserRepository appUserRepository,
                             DoctorRepository doctorRepository, Clock clock) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.appUserRepository = appUserRepository;
        this.doctorRepository = doctorRepository;
        this.clock = clock;
    }

    @Transactional
    public CreatedStaff createStaff(RlsContext caller, String email, String passwordHash, Role role,
                                     UUID departmentId, String firstName, String lastName, String bio) {
        rlsSessionInitializer.applyCurrentContext(caller);

        Instant now = clock.instant();
        AppUser user = new AppUser(UUID.randomUUID(), email, passwordHash, role, firstName, lastName, now);
        if (departmentId != null) {
            user.setDepartmentId(departmentId);
        }
        appUserRepository.save(user);

        UUID doctorId = null;
        if (role == Role.DOCTOR) {
            Doctor doctor = doctorRepository.save(new Doctor(UUID.randomUUID(), user.getId(), departmentId, bio, now));
            doctorId = doctor.getId();
        }

        return new CreatedStaff(user, doctorId);
    }

    @Transactional(readOnly = true)
    public List<AppUser> listVisibleStaff(RlsContext caller) {
        rlsSessionInitializer.applyCurrentContext(caller);
        return appUserRepository.findAll().stream().filter(u -> u.getRole() != Role.PATIENT).toList();
    }

    public record CreatedStaff(AppUser user, UUID doctorId) {
    }
}
