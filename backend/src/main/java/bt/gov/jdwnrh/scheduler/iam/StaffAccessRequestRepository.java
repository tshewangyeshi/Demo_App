package bt.gov.jdwnrh.scheduler.iam;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RLS-scoped for anything except the public INSERT (see V16) — SELECT/UPDATE
 * calls require an applied RlsContext (admin/department-admin), same as
 * AppUserRepository.
 */
public interface StaffAccessRequestRepository extends JpaRepository<StaffAccessRequest, UUID> {

    List<StaffAccessRequest> findByStatusOrderByCreatedAt(StaffAccessRequestStatus status);
}
