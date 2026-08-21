package bt.gov.jdwnrh.scheduler.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.iam.AccessRequestAlreadyReviewedException;
import bt.gov.jdwnrh.scheduler.iam.Role;
import bt.gov.jdwnrh.scheduler.iam.StaffAccessRequest;
import bt.gov.jdwnrh.scheduler.iam.StaffAccessRequestService;
import bt.gov.jdwnrh.scheduler.iam.StaffAccessRequestStatus;

/**
 * Admin review of pending staff/doctor access requests submitted via
 * StaffAccessRequestController. RLS (V16) is the real gate on which
 * requests a caller can see/act on — AdminScope isn't needed here the way
 * it is for department/specialty/etc. (which aren't RLS-protected).
 */
@RestController
@RequestMapping("/api/admin/access-requests")
public class AdminAccessRequestController {

    private final AdminScope adminScope;
    private final StaffAccessRequestService service;

    public AdminAccessRequestController(AdminScope adminScope, StaffAccessRequestService service) {
        this.adminScope = adminScope;
        this.service = service;
    }

    @GetMapping
    public List<AccessRequestResponse> list(@RequestParam(required = false) StaffAccessRequestStatus status) {
        RlsContext caller = adminScope.require();
        return service.listVisible(caller, status).stream().map(AccessRequestResponse::from).toList();
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        RlsContext caller = adminScope.require();
        service.approve(caller, id);
        // 204, not 200: see AuthController.logout() for why an empty-body 200
        // silently breaks the frontend's apiFetch<void> callers.
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        RlsContext caller = adminScope.require();
        service.reject(caller, id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AccessRequestAlreadyReviewedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyReviewed(AccessRequestAlreadyReviewedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    public record ErrorResponse(String message) {
    }

    public record RejectRequest(String reason) {
    }

    /** Deliberately excludes passwordHash — never send a stored hash, even to an admin, over the wire. */
    public record AccessRequestResponse(
            UUID id, String email, Role requestedRole, UUID departmentId, String firstName, String lastName,
            String bio, StaffAccessRequestStatus status, Instant createdAt, Instant reviewedAt, String rejectionReason) {

        static AccessRequestResponse from(StaffAccessRequest r) {
            return new AccessRequestResponse(r.getId(), r.getEmail(), r.getRequestedRole(), r.getDepartmentId(),
                    r.getFirstName(), r.getLastName(), r.getBio(), r.getStatus(), r.getCreatedAt(),
                    r.getReviewedAt(), r.getRejectionReason());
        }
    }
}
