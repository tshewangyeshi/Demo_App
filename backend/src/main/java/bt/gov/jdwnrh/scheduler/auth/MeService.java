package bt.gov.jdwnrh.scheduler.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;
import bt.gov.jdwnrh.scheduler.iam.AppUser;
import bt.gov.jdwnrh.scheduler.iam.AppUserRepository;

/** Own @Transactional boundary because RlsSessionInitializer.applyCurrentContext requires one (Propagation.MANDATORY) — see MeController. */
@Service
public class MeService {

    private final RlsSessionInitializer rlsSessionInitializer;
    private final AppUserRepository appUserRepository;

    public MeService(RlsSessionInitializer rlsSessionInitializer, AppUserRepository appUserRepository) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public AppUser getCurrentProfile(RlsContext caller) {
        rlsSessionInitializer.applyCurrentContext(caller);
        // app_user_self_or_admin's "id = current_user_id" clause always makes this visible to its own owner.
        return appUserRepository.findById(caller.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no app_user row: " + caller.userId()));
    }
}
