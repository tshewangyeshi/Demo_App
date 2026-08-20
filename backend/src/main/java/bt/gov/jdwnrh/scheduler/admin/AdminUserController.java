package bt.gov.jdwnrh.scheduler.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.auth.AuthLookupRepository;
import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.iam.Role;

/**
 * Staff/doctor/admin account provisioning — see V7__self_registration_policy.sql:
 * these roles are never self-registered. Mirrors V14's RLS policies
 * (app_user_department_admin_provision) as an app-layer pre-check so a
 * misconfigured request fails with a clear 4xx instead of a raw RLS-filtered
 * DB error. All the actual DB work lives in AdminUserService (needs its own
 * @Transactional boundary — see RlsSessionInitializer's Propagation.MANDATORY).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminScope adminScope;
    private final AdminUserService adminUserService;
    private final AuthLookupRepository authLookupRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(AdminScope adminScope, AdminUserService adminUserService,
                                AuthLookupRepository authLookupRepository, PasswordEncoder passwordEncoder) {
        this.adminScope = adminScope;
        this.adminUserService = adminUserService;
        this.authLookupRepository = authLookupRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/staff")
    public ResponseEntity<StaffResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        if (request.role() == Role.PATIENT) {
            throw new IllegalArgumentException("Patients self-register at /api/auth/register, not here");
        }

        RlsContext caller = adminScope.require();
        boolean departmentScopedRole = request.role() == Role.DOCTOR || request.role() == Role.NURSE
                || request.role() == Role.RECEPTIONIST;

        if (departmentScopedRole) {
            if (request.departmentId() == null) {
                throw new IllegalArgumentException(request.role() + " requires a departmentId");
            }
            adminScope.requireDepartmentAccess(caller, request.departmentId());
        } else {
            // DEPARTMENT_ADMIN, HOSPITAL_ADMIN, SUPER_ADMIN accounts are hospital-admin territory —
            // matches V14's app_user_department_admin_provision, which only covers the three roles above.
            adminScope.requireHospitalWide(caller);
            if (request.role() == Role.DEPARTMENT_ADMIN && request.departmentId() == null) {
                throw new IllegalArgumentException("DEPARTMENT_ADMIN requires a departmentId");
            }
        }

        if (authLookupRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        var created = adminUserService.createStaff(caller, request.email(), passwordEncoder.encode(request.temporaryPassword()),
                request.role(), request.departmentId(), request.firstName(), request.lastName(), request.bio());

        return ResponseEntity.status(HttpStatus.CREATED).body(new StaffResponse(
                created.user().getId(), created.user().getEmail(), created.user().getRole(),
                created.user().getDepartmentId(), created.doctorId()));
    }

    @GetMapping("/staff")
    public List<StaffResponse> listMyDepartmentStaff() {
        // RLS (app_user_department_admin_view / app_user_self_or_admin) does the actual
        // scoping: a department admin sees their department, hospital/super admin sees all.
        return adminUserService.listVisibleStaff(adminScope.require()).stream()
                .map(u -> new StaffResponse(u.getId(), u.getEmail(), u.getRole(), u.getDepartmentId(), null))
                .toList();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
    }

    public record ErrorResponse(String message) {
    }

    public record CreateStaffRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String temporaryPassword,
            @NotNull Role role,
            UUID departmentId,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String bio) {
    }

    public record StaffResponse(UUID id, String email, Role role, UUID departmentId, UUID doctorId) {
    }
}
