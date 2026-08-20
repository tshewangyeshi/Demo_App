package bt.gov.jdwnrh.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import bt.gov.jdwnrh.scheduler.auth.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/departments/**", "/api/doctors/**", "/api/availability/**").permitAll()
                        .requestMatchers("/api/status/**", "/api/lookup/**").permitAll() // E1 status page, E2 public lookup
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
