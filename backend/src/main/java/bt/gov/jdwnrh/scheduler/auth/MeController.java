package bt.gov.jdwnrh.scheduler.auth;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.iam.AppUser;
import bt.gov.jdwnrh.scheduler.iam.Role;

/**
 * Everything else under /api/auth/** is deliberately permitAll (see
 * SecurityConfig) — this one endpoint needs the opposite, so SecurityConfig
 * carves it out with a matcher ahead of the broader /api/auth/** rule.
 */
@RestController
public class MeController {

    private final CurrentUser currentUser;
    private final MeService meService;

    public MeController(CurrentUser currentUser, MeService meService) {
        this.currentUser = currentUser;
        this.meService = meService;
    }

    @GetMapping("/api/auth/me")
    public MeResponse me() {
        RlsContext caller = currentUser.require();
        AppUser user = meService.getCurrentProfile(caller);
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(), user.getDepartmentId(),
                user.getFirstName(), user.getLastName());
    }

    public record MeResponse(UUID id, String email, Role role, UUID departmentId, String firstName, String lastName) {
    }
}
