package bt.gov.jdwnrh.scheduler.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import bt.gov.jdwnrh.scheduler.auth.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The refresh cookie is SameSite=None (see AuthController) specifically
     * because the SPA frontend and this backend are deliberately on
     * different origins. SameSite=None alone isn't enough — the browser
     * also refuses to let JS read a cross-origin response with credentials
     * unless the server echoes back the EXACT calling origin (never "*",
     * which browsers reject when credentials are involved) plus
     * Access-Control-Allow-Credentials: true.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true); // required for the browser to send/accept the refresh cookie cross-origin

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // Access tokens ride the Authorization header (not a cookie), so CSRF
                // isn't a concern for most endpoints — CSRF exploits ambient
                // cookie-based auth the browser attaches automatically. The refresh
                // token IS an httpOnly cookie, so /api/auth/refresh and /api/auth/logout
                // are a real (low-severity) CSRF surface: a forged cross-site request
                // could trigger a token rotation or log the user out. Deliberately
                // simplified for this build's scope rather than adding full CSRF-token
                // infrastructure — noted here, not silently skipped.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {}) // picks up the corsConfigurationSource bean above
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot's default error handling calls response.sendError(), which
                        // triggers a server-side FORWARD to /error (BasicErrorController) — a
                        // SEPARATE dispatch that re-runs this same filter chain. Without this
                        // permitAll, an unauthenticated caller hitting a validation error (or any
                        // uncaught exception) on a permitAll endpoint never sees the real error:
                        // /error falls through to .anyRequest().authenticated() below, which denies
                        // it, replacing the real 400/500 with a bare empty 403. This is the root
                        // cause of the "mystery bare 403" pattern noted repeatedly in README.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/me").authenticated() // carve-out: matched before the broader permitAll below
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/departments/**", "/api/doctors/**", "/api/availability/**").permitAll()
                        .requestMatchers("/api/status/**", "/api/lookup/**").permitAll() // E1 status page, E2 public lookup
                        // Path-prefix gates by role — fine-grained scoping (e.g. a DEPARTMENT_ADMIN
                        // only touching their own department) happens inside each controller/service
                        // via CurrentUser, same as RLS does for row-level access.
                        .requestMatchers("/api/admin/**").hasAnyRole("DEPARTMENT_ADMIN", "HOSPITAL_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/staff/**").hasAnyRole("NURSE", "RECEPTIONIST", "DEPARTMENT_ADMIN", "HOSPITAL_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/doctor-portal/**").hasRole("DOCTOR")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
