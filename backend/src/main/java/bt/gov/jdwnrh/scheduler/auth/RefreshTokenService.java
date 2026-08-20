package bt.gov.jdwnrh.scheduler.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens are opaque random values, never JWTs — only their SHA-256
 * hash is stored (see V5__refresh_tokens.sql), so a DB read can't be replayed
 * as a live session. Rotated on every use: refresh burns the old token and
 * issues a new one, so a stolen-and-reused old refresh token is detectable
 * (it will already be revoked).
 */
@Service
public class RefreshTokenService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public String issue(UUID userId) {
        String rawToken = generateRawToken();
        RefreshToken entity = new RefreshToken(
                UUID.randomUUID(), userId, hash(rawToken), clock.instant().plus(REFRESH_TOKEN_TTL));
        repository.save(entity);
        return rawToken;
    }

    /** Validates the raw token, revokes it, and issues a replacement. Returns empty if the token is invalid/expired/already-revoked. */
    @Transactional
    public Optional<Rotated> rotate(String rawToken) {
        Optional<RefreshToken> found = repository.findByTokenHash(hash(rawToken));
        if (found.isEmpty() || !found.get().isValid(clock.instant())) {
            return Optional.empty();
        }
        RefreshToken old = found.get();
        old.revoke(clock.instant());
        String newRawToken = issue(old.getUserId());
        return Optional.of(new Rotated(old.getUserId(), newRawToken));
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record Rotated(UUID userId, String newRawToken) {
    }
}
