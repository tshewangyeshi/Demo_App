package bt.gov.jdwnrh.scheduler.iam;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.admin.AdminUserService;
import bt.gov.jdwnrh.scheduler.audit.AuditLogger;
import bt.gov.jdwnrh.scheduler.auth.AuthLookupRepository;
import bt.gov.jdwnrh.scheduler.config.RlsContext;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;

/**
 * Admin-side review of pending requests — RLS (see V16) is what actually
 * scopes a department admin to their own department; this only needs its
 * own @Transactional boundary because RlsSessionInitializer.applyCurrentContext
 * requires one (Propagation.MANDATORY), same lesson as AdminUserService.
 * Public submission (no RlsContext exists yet) lives in
 * StaffAccessRequestController instead, mirroring AuthController.register().
 */
@Service
public class StaffAccessRequestService {

    private final RlsSessionInitializer rlsSessionInitializer;
    private final StaffAccessRequestRepository repository;
    private final AdminUserService adminUserService;
    private final AuthLookupRepository authLookupRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public StaffAccessRequestService(RlsSessionInitializer rlsSessionInitializer, StaffAccessRequestRepository repository,
                                      AdminUserService adminUserService, AuthLookupRepository authLookupRepository,
                                      AuditLogger auditLogger, Clock clock) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.repository = repository;
        this.adminUserService = adminUserService;
        this.authLookupRepository = authLookupRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffAccessRequest> listVisible(RlsContext caller, StaffAccessRequestStatus status) {
        rlsSessionInitializer.applyCurrentContext(caller);
        return status != null ? repository.findByStatusOrderByCreatedAt(status) : repository.findAll();
    }

    @Transactional
    public AdminUserService.CreatedStaff approve(RlsContext caller, UUID requestId) {
        rlsSessionInitializer.applyCurrentContext(caller);

        StaffAccessRequest request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + requestId));
        if (request.getStatus() != StaffAccessRequestStatus.PENDING) {
            throw new AccessRequestAlreadyReviewedException("This request has already been reviewed");
        }
        if (authLookupRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AccessRequestAlreadyReviewedException("An account with this email already exists");
        }

        AdminUserService.CreatedStaff created = adminUserService.createStaff(
                caller, request.getEmail(), request.getPasswordHash(), request.getRequestedRole(),
                request.getDepartmentId(), request.getFirstName(), request.getLastName(), request.getBio());

        request.approve(caller.userId(), clock.instant());
        repository.save(request);

        auditLogger.log(caller.userId(), "APPROVE", "ACCESS_REQUEST", requestId,
                Map.of("status", "PENDING"), Map.of("status", "APPROVED", "createdUserId", created.user().getId().toString()));

        return created;
    }

    @Transactional
    public void reject(RlsContext caller, UUID requestId, String reason) {
        rlsSessionInitializer.applyCurrentContext(caller);

        StaffAccessRequest request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + requestId));
        if (request.getStatus() != StaffAccessRequestStatus.PENDING) {
            throw new AccessRequestAlreadyReviewedException("This request has already been reviewed");
        }

        request.reject(caller.userId(), reason, clock.instant());
        repository.save(request);

        auditLogger.log(caller.userId(), "REJECT", "ACCESS_REQUEST", requestId,
                Map.of("status", "PENDING"), Map.of("status", "REJECTED", "reason", reason == null ? "" : reason));
    }
}
