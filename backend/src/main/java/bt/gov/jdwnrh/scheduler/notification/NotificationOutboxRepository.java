package bt.gov.jdwnrh.scheduler.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** notification_outbox carries no RLS (see V3's closing comment) — a background job can query it freely, no RlsContext needed. */
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    /**
     * FOR UPDATE SKIP LOCKED so multiple backend instances polling
     * concurrently never both grab the same row — same reasoning as the
     * reminder-claim functions (see V18), just plain SQL here since this
     * table has no RLS to bypass.
     */
    @Query(value = """
            SELECT id FROM notification_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= now()
            ORDER BY next_attempt_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> claimPendingIds(int batchSize);
}
