package bt.gov.jdwnrh.scheduler.iam;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A prospective doctor/nurse/receptionist's request for an account — see
 * V16__staff_access_requests.sql. Submitted publicly (no auth), reviewed by
 * an admin (see StaffAccessRequestService), and only becomes a real
 * app_user row on approval. DEPARTMENT_ADMIN and above are never
 * requestable this way — those accounts are created directly via
 * AdminUserController.
 */
@Entity
@Table(name = "staff_access_request")
public class StaffAccessRequest {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false)
    private Role requestedRole;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffAccessRequestStatus status = StaffAccessRequestStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StaffAccessRequest() {
        // JPA
    }

    public StaffAccessRequest(UUID id, String email, String passwordHash, Role requestedRole, UUID departmentId,
                               String firstName, String lastName, String bio, Instant now) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.requestedRole = requestedRole;
        this.departmentId = departmentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.bio = bio;
        this.createdAt = now;
    }

    public void approve(UUID reviewerId, Instant now) {
        if (status != StaffAccessRequestStatus.PENDING) {
            throw new IllegalStateException("Request " + id + " is already " + status);
        }
        this.status = StaffAccessRequestStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.passwordHash = null; // no longer needed once the real app_user row exists — don't keep it around
    }

    public void reject(UUID reviewerId, String reason, Instant now) {
        if (status != StaffAccessRequestStatus.PENDING) {
            throw new IllegalStateException("Request " + id + " is already " + status);
        }
        this.status = StaffAccessRequestStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.rejectionReason = reason;
        this.passwordHash = null;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRequestedRole() {
        return requestedRole;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getBio() {
        return bio;
    }

    public StaffAccessRequestStatus getStatus() {
        return status;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
