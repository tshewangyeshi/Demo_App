package bt.gov.jdwnrh.scheduler.iam;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query through this repository is subject to app_user's RLS policies
 * (see V3__row_level_security.sql) — it requires an RlsContext to already be
 * applied for the current transaction. There is deliberately NO
 * findByEmail() here: that lookup happens before any RlsContext exists
 * (during login itself), so it goes through the narrow SECURITY DEFINER
 * function in {@link bt.gov.jdwnrh.scheduler.auth.AuthLookupRepository}
 * instead, never through this RLS-scoped repository.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
