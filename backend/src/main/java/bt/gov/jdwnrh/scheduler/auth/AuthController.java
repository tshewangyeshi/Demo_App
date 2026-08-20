package bt.gov.jdwnrh.scheduler.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import bt.gov.jdwnrh.scheduler.iam.AppUser;
import bt.gov.jdwnrh.scheduler.iam.AppUserRepository;
import bt.gov.jdwnrh.scheduler.iam.Role;

@RestController
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AppUserRepository appUserRepository;
    private final AuthLookupRepository authLookupRepository;
    private final CurrentRoleLookup currentRoleLookup;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public AuthController(AppUserRepository appUserRepository, AuthLookupRepository authLookupRepository,
                           CurrentRoleLookup currentRoleLookup, PasswordEncoder passwordEncoder,
                           JwtService jwtService, RefreshTokenService refreshTokenService, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.authLookupRepository = authLookupRepository;
        this.currentRoleLookup = currentRoleLookup;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<AccessTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (authLookupRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        AppUser user = new AppUser(
                UUID.randomUUID(),
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.PATIENT,
                request.firstName(),
                request.lastName(),
                clock.instant());
        appUserRepository.save(user);

        return issueTokens(user.getId(), Role.PATIENT, null);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var credentials = authLookupRepository.findByEmail(request.email()).orElse(null);

        if (credentials == null || !passwordEncoder.matches(request.password(), credentials.passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"ACTIVE".equals(credentials.accountStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return issueTokens(credentials.id(), credentials.role(), credentials.departmentId());
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie) {

        if (refreshCookie == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var rotated = refreshTokenService.rotate(refreshCookie).orElse(null);
        if (rotated == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Re-derive the (possibly-changed) role/department for the new access
        // token — same DB re-check JwtAuthenticationFilter does per-request —
        // rather than trusting whatever role the old token carried.
        var current = currentRoleLookup.findByUserId(rotated.userId()).orElse(null);
        if (current == null || !"ACTIVE".equals(current.accountStatus())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String newAccessToken = jwtService.issueAccessToken(rotated.userId(), current.role(), current.departmentId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(rotated.newRawToken()).toString())
                .body(AccessTokenResponse.bearer(newAccessToken));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie) {
        if (refreshCookie != null) {
            refreshTokenService.revoke(refreshCookie);
        }
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true).secure(true).sameSite("None").path("/api/auth").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    private ResponseEntity<AccessTokenResponse> issueTokens(UUID userId, Role role, UUID departmentId) {
        String accessToken = jwtService.issueAccessToken(userId, role, departmentId);
        String refreshToken = refreshTokenService.issue(userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(refreshToken).toString())
                .body(AccessTokenResponse.bearer(accessToken));
    }

    private ResponseCookie buildRefreshCookie(String rawToken) {
        // SameSite=None (not Strict): the frontend (Vercel) and backend
        // (Railway/Render) are deliberately on different origins per the
        // Distribution Plan — a Strict cookie would never be sent on the
        // SPA's cross-origin fetch to /api/auth/refresh, silently breaking
        // the refresh flow the moment frontend and backend aren't on the
        // same domain. None requires Secure, which is already set.
        return ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/api/auth")
                .maxAge(Duration.ofDays(7))
                .build();
    }
}
