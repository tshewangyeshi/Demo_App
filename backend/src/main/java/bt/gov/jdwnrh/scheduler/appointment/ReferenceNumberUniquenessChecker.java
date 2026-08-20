package bt.gov.jdwnrh.scheduler.appointment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

/** Calls reference_number_active_exists (see V19) — see BookingService for why this can't just be an RLS-scoped repository query. */
@Component
public class ReferenceNumberUniquenessChecker {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean isActiveReferenceNumber(String referenceNumber) {
        return (Boolean) entityManager.createNativeQuery("SELECT reference_number_active_exists(:referenceNumber)")
                .setParameter("referenceNumber", referenceNumber)
                .getSingleResult();
    }
}
