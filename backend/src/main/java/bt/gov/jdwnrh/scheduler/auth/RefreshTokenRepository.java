package bt.gov.jdwnrh.scheduler.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Not RLS-protected — see V5__refresh_tokens.sql. Scoped by userId in every query, not by session context. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
