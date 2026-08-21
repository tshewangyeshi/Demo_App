package bt.gov.jdwnrh.scheduler.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.config.InMemoryRateLimiter;
import bt.gov.jdwnrh.scheduler.iam.Role;
import bt.gov.jdwnrh.scheduler.iam.StaffAccessRequest;
import bt.gov.jdwnrh.scheduler.iam.StaffAccessRequestRepository;
import bt.gov.jdwnrh.scheduler.iam.StaffAccessRequestStatus;

/**
 * Public (no auth — see SecurityConfig's /api/auth/** permitAll) submission
 * of a staff/doctor account request. Deliberately does NOT create a real
 * account or issue tokens — see V16__staff_access_requests.sql: the
 * request only becomes an app_user row once an admin approves it
 * (AdminAccessRequestController). Only DOCTOR/NURSE/RECEPTIONIST are
 * requestable this way; DEPARTMENT_ADMIN and above stay admin-console-only.
 */
@RestController
public class StaffAccessRequestController {

    private static final Set<Role> REQUESTABLE_ROLES = Set.of(Role.DOCTOR, Role.NURSE, Role.RECEPTIONIST);

    // Same abuse profile as AuthController.register() — public, unauthenticated,
    // email+password — and this codebase's own /review caught that this endpoint
    // was missed when rate limiting was first added there. Per-IP only (no
    // per-email key): unlike login, there's no "target account" to protect here,
    // and an email-keyed bucket on unauthenticated attacker-controlled input is
    // exactly the unbounded-growth risk InMemoryRateLimiter now guards against.
    private static final int SUBMIT_MAX_ATTEMPTS_PER_IP = 10;
    private static final Duration SUBMIT_WINDOW = Duration.ofMinutes(15);

    private final StaffAccessRequestRepository repository;
    private final AuthLookupRepository authLookupRepository;
    private final PasswordEncoder passwordEncoder;
    private final InMemoryRateLimiter rateLimiter;
    private final Clock clock;

    public StaffAccessRequestController(StaffAccessRequestRepository repository, AuthLookupRepository authLookupRepository,
                                         PasswordEncoder passwordEncoder, InMemoryRateLimiter rateLimiter, Clock clock) {
        this.repository = repository;
        this.authLookupRepository = authLookupRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @PostMapping("/api/auth/staff-access-requests")
    public ResponseEntity<SubmitResponse> submit(@Valid @RequestBody SubmitRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire("staff-request-ip:" + httpRequest.getRemoteAddr(), SUBMIT_MAX_ATTEMPTS_PER_IP, SUBMIT_WINDOW)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        if (!REQUESTABLE_ROLES.contains(request.requestedRole())) {
            return ResponseEntity.badRequest().build();
        }
        if (authLookupRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        StaffAccessRequest saved = repository.save(new StaffAccessRequest(
                UUID.randomUUID(), request.email(), passwordEncoder.encode(request.password()), request.requestedRole(),
                request.departmentId(), request.firstName(), request.lastName(), request.bio(), clock.instant()));

        return ResponseEntity.status(HttpStatus.CREATED).body(new SubmitResponse(saved.getId(), saved.getStatus()));
    }

    public record SubmitRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotNull Role requestedRole,
            @NotNull UUID departmentId,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String bio) {
    }

    public record SubmitResponse(UUID id, StaffAccessRequestStatus status) {
    }
}
