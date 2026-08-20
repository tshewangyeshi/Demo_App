package bt.gov.jdwnrh.scheduler.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import bt.gov.jdwnrh.scheduler.iam.Role;

/**
 * Issues and verifies short-lived access tokens (~15 min — see design doc,
 * "Auth mechanism: JWT (stateless), short-lived, httpOnly refresh"). Refresh
 * tokens are NOT JWTs — they're opaque, server-tracked, revocable tokens
 * (see RefreshTokenService) precisely because a stateless JWT can't be
 * revoked before it expires, which is unacceptable for a long-lived
 * credential.
 */
@Service
public class JwtService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final SecretKey signingKey;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String secret, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String issueAccessToken(UUID userId, Role role, UUID departmentId) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)));

        if (departmentId != null) {
            builder.claim("departmentId", departmentId.toString());
        }

        return builder.signWith(signingKey).compact();
    }

    public VerifiedAccessToken verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        String departmentIdClaim = claims.get("departmentId", String.class);
        UUID departmentId = departmentIdClaim != null ? UUID.fromString(departmentIdClaim) : null;

        return new VerifiedAccessToken(userId, role, departmentId);
    }

    public record VerifiedAccessToken(UUID userId, Role role, UUID departmentId) {
    }
}
