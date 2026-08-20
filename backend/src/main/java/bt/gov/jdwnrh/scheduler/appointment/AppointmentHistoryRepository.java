package bt.gov.jdwnrh.scheduler.appointment;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentHistoryRepository extends JpaRepository<AppointmentHistory, UUID> {
}
