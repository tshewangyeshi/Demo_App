package bt.gov.jdwnrh.scheduler.notification;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
}
