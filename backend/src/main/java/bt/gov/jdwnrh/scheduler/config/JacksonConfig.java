package bt.gov.jdwnrh.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Defined explicitly rather than relying on Spring Boot's Jackson
 * autoconfiguration — that wasn't producing an ObjectMapper bean in this
 * Spring Boot 4.1 setup even with spring-boot-starter-json on the
 * classpath, for reasons not worth chasing further. NotificationEnqueuer
 * needs this to serialize outbox payloads.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
