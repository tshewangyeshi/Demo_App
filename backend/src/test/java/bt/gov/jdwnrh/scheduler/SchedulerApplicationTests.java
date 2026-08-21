package bt.gov.jdwnrh.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// app.jwt.secret has no default in application.yml on purpose (see /cso
// security audit, 2026-08-21, Finding 1 — a hardcoded fallback there would
// be a real credential leak). Without this override, `mvn test` silently
// depended on the shell already having APP_JWT_SECRET exported — true on
// this machine (run-local.sh sets it), false on a fresh clone or CI. A
// throwaway test/resources/application.yml is NOT the fix: Spring Boot
// resolves classpath:/application.yml to a single resource, and test-classes
// comes first on the test classpath, so it would SHADOW (not merge with)
// src/main/resources/application.yml, silently dropping the datasource
// config too. @TestPropertySource adds a property on top instead.
@SpringBootTest
@TestPropertySource(properties = "app.jwt.secret=test-only-secret-never-used-outside-mvn-test-0123456789")
class SchedulerApplicationTests {

	@Test
	void contextLoads() {
	}

}
