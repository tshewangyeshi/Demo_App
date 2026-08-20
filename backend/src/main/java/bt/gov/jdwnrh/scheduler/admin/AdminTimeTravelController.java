package bt.gov.jdwnrh.scheduler.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.audit.AuditLogger;
import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.config.TimeTravelClock;

/**
 * See docs/designs/jdwnrh-scheduler.md, "Scope Expansion" -> E4. Deliberately
 * narrower than the rest of /api/admin/** — the design doc restricts this to
 * SUPER_ADMIN/HOSPITAL_ADMIN specifically ("a capability that shifts the
 * whole app's clock warrants the narrowest RBAC tier"), so this calls
 * requireHospitalWide() itself rather than relying on SecurityConfig's
 * broader DEPARTMENT_ADMIN-inclusive /api/admin/** gate.
 */
@RestController
@RequestMapping("/api/admin/time-travel")
public class AdminTimeTravelController {

    private final AdminScope adminScope;
    private final AuditLogger auditLogger;
    private final TimeTravelClock clock;

    public AdminTimeTravelController(AdminScope adminScope, AuditLogger auditLogger, TimeTravelClock clock) {
        this.adminScope = adminScope;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @GetMapping
    public StatusResponse status() {
        adminScope.requireHospitalWide(adminScope.require());
        return StatusResponse.from(clock);
    }

    @PostMapping("/advance")
    public StatusResponse advance(@Valid @RequestBody AdvanceRequest request) {
        RlsContext caller = adminScope.require();
        adminScope.requireHospitalWide(caller);
        Duration previousOffset = clock.getOffset();
        clock.advance(Duration.ofDays(request.days()));
        auditLogger.log(caller.userId(), "ADVANCE", "TIME_TRAVEL", null,
                Map.of("offsetDays", previousOffset.toDays()), Map.of("offsetDays", clock.getOffset().toDays()));
        return StatusResponse.from(clock);
    }

    @PostMapping("/reset")
    public StatusResponse reset() {
        RlsContext caller = adminScope.require();
        adminScope.requireHospitalWide(caller);
        Duration previousOffset = clock.getOffset();
        clock.reset();
        auditLogger.log(caller.userId(), "RESET", "TIME_TRAVEL", null,
                Map.of("offsetDays", previousOffset.toDays()), Map.of("offsetDays", 0));
        return StatusResponse.from(clock);
    }

    public record AdvanceRequest(@NotNull Long days) {
    }

    public record StatusResponse(Instant currentSimulatedTime, long offsetDays) {
        static StatusResponse from(TimeTravelClock clock) {
            return new StatusResponse(clock.instant(), clock.getOffset().toDays());
        }
    }
}
