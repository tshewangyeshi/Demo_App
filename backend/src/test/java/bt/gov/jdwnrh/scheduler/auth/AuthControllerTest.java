package bt.gov.jdwnrh.scheduler.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context integration tests (not @WebMvcTest — that slice does not
 * reliably load a user-defined SecurityFilterChain bean, and the whole
 * point of the /error test below is proving the REAL SecurityConfig
 * behaves correctly). RANDOM_PORT (a real embedded Tomcat) rather than
 * MOCK: the /error regression below depends on the container's genuine
 * internal forward-on-sendError mechanism, which MockMvc's simulated
 * dispatch does not reproduce — confirmed empirically (asserting a
 * non-empty error body failed under MOCK even with the fix correctly
 * in place, because MockMvc never invokes the real BasicErrorController).
 * Uses the JDK's built-in HttpClient for that one test rather than
 * TestRestTemplate — this project has no spring-boot-restclient
 * dependency (it makes no outbound REST calls of its own), and
 * TestRestTemplate's autoconfiguration fails to load without it
 * (NoClassDefFoundError: RestTemplateBuilder). Not worth adding a new
 * dependency just for one test when the JDK client does the job.
 * Uses a distinct fake remote IP per test method (see fromIp) so tests
 * sharing this Spring context don't bleed into each other's per-IP
 * rate-limit budget — InMemoryRateLimiter is a singleton bean, reused
 * across all test methods in this class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.jwt.secret=test-only-secret-never-used-outside-mvn-test-0123456789")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    // Regression: /review, 2026-08-21 — SecurityConfig.java. Spring Boot's
    // default error handling calls response.sendError(), which forwards to
    // /error internally — a SEPARATE dispatch that re-runs the security
    // filter chain. Before adding .requestMatchers("/error").permitAll(),
    // that forward fell through to .anyRequest().authenticated() and got
    // denied, replacing the real 400 (a routine Bean Validation failure —
    // missing email/password) with a bare empty 403. This is the exact
    // repro that surfaced the bug in the first place. Uses a real HTTP call
    // against the real embedded server, not MockMvc — see the class-level
    // comment for why MockMvc can't faithfully reproduce this.
    @Test
    void unauthenticatedValidationFailureReturnsRealBadRequestNotBareForbidden() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertFalse(response.body() == null || response.body().isBlank(),
                "expected a real error body, got the bare-403-style empty response the /error permitAll fix prevents");
    }

    // Regression: /review, 2026-08-21 — AuthController.login() had zero rate
    // limiting before today; this proves the fix. Per-email budget (5/15min)
    // trips regardless of the per-IP budget (20/15min) not yet being spent.
    @Test
    void loginTripsPerEmailRateLimitAfterFiveAttempts() throws Exception {
        String body = """
                {"email":"rate-limit-target@example.com","password":"wrong-password"}""";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(fromIp("10.10.10.2")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(fromIp("10.10.10.2")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginRateLimitIsScopedPerEmailNotGlobal() throws Exception {
        String exhausted = """
                {"email":"already-exhausted@example.com","password":"wrong"}""";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(exhausted)
                            .with(fromIp("10.10.10.3")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exhausted)
                        .with(fromIp("10.10.10.3")))
                .andExpect(status().isTooManyRequests());

        // A different email from the SAME IP is unaffected — the exhausted
        // email's budget doesn't leak into a sibling account's budget.
        String different = """
                {"email":"unaffected-account@example.com","password":"wrong"}""";
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(different)
                        .with(fromIp("10.10.10.3")))
                .andExpect(status().isUnauthorized()); // not 429 — real credential check ran
    }

    // Regression: /review, 2026-08-21 — logout() returned 200 with an empty
    // body (ResponseEntity.ok().build()), which made the frontend's apiFetch
    // throw a SyntaxError trying to parse the empty body as JSON, silently
    // aborting the post-logout UI update. Fixed to 204. No refresh cookie
    // needed for this path: logout() skips revoke() entirely when none is
    // present, so this doesn't depend on a real refresh token existing.
    @Test
    void logoutReturns204NotEmpty200() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(fromIp("10.10.10.4")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    // Regression: /review adversarial pass, 2026-08-21, Finding 1 —
    // InMemoryRateLimiter keys its per-IP login bucket on
    // request.getRemoteAddr(), which behind a reverse proxy returns the
    // PROXY's address for every caller unless Spring resolves it from
    // X-Forwarded-For. Proves server.forward-headers-strategy: framework
    // actually takes effect, not just that the config line exists: 20
    // requests carrying the SAME X-Forwarded-For (each a distinct email, so
    // the per-email budget never trips) exhaust one shared per-IP bucket,
    // while a DIFFERENT X-Forwarded-For gets its own fresh budget. Real HTTP
    // client against the real embedded server — MockMvc's
    // request.setRemoteAddr() bypasses the servlet filter chain entirely and
    // would prove nothing about whether ForwardedHeaderFilter is wired up.
    @Test
    void ipRateLimitBucketIsScopedByForwardedForHeaderNotRawSocketAddress() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < 20; i++) {
            assertEquals(401, loginAttempt(client, "xff-ip-target+" + i + "@example.com", "10.20.30.40"),
                    "attempt " + (i + 1) + " should be a real auth failure, not yet rate limited");
        }
        assertEquals(429, loginAttempt(client, "xff-ip-target+overflow@example.com", "10.20.30.40"),
                "21st attempt from the same forwarded IP must trip the per-IP budget");

        // A different forwarded IP must have its own fresh budget. If
        // getRemoteAddr() were NOT resolving X-Forwarded-For, this would
        // also be 429 — the underlying JDK HttpClient connection is the
        // same loopback socket either way.
        assertEquals(401, loginAttempt(client, "xff-ip-target+overflow@example.com", "10.20.30.41"),
                "a different forwarded IP must not share the exhausted IP's budget");
    }

    private int loginAttempt(HttpClient client, String email, String forwardedFor) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
