package bt.gov.jdwnrh.scheduler.auth;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

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

    private final StaffAccessRequestRepository repository;
    private final AuthLookupRepository authLookupRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public StaffAccessRequestController(StaffAccessRequestRepository repository, AuthLookupRepository authLookupRepository,
                                         PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.authLookupRepository = authLookupRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @PostMapping("/api/auth/staff-access-requests")
    public ResponseEntity<SubmitResponse> submit(@Valid @RequestBody SubmitRequest request) {
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
