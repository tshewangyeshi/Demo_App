package bt.gov.jdwnrh.scheduler.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;

import org.springframework.stereotype.Repository;

/**
 * GROUP BY aggregate queries against `appointment` — never per-row
 * application loops (see design doc, "Admin reporting via single GROUP BY
 * aggregate queries"). RLS does the real scoping here: appointment's
 * existing policies already let a department admin see every appointment
 * for doctors in their own department (not just ones they personally
 * touched) via appointment_department_scoped_staff, and hospital/super
 * admin see everything via appointment_hospital_admin_all — so these
 * aggregates are automatically correct per-caller with no separate
 * SECURITY DEFINER bypass needed. Caller must have already applied an
 * RlsContext in this transaction (see ReportingService).
 */
@Repository
public class ReportingRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<StatusCount> countByStatus(Instant from, Instant to) {
        var rows = (List<Tuple>) entityManager.createNativeQuery("""
                SELECT status, COUNT(*) AS cnt
                FROM appointment
                WHERE start_time >= :from AND start_time < :to
                GROUP BY status
                """, Tuple.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows.stream()
                .map(row -> new StatusCount((String) row.get("status"), ((Number) row.get("cnt")).longValue()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<DoctorCount> countByDoctor(Instant from, Instant to) {
        var rows = (List<Tuple>) entityManager.createNativeQuery("""
                SELECT doctor_id, COUNT(*) AS cnt
                FROM appointment
                WHERE start_time >= :from AND start_time < :to
                GROUP BY doctor_id
                ORDER BY cnt DESC
                """, Tuple.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows.stream()
                .map(row -> new DoctorCount((UUID) row.get("doctor_id"), ((Number) row.get("cnt")).longValue()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<DayCount> countByDay(Instant from, Instant to) {
        // Grouped in Asia/Thimphu, not UTC — a "day" for reporting means the hospital's calendar day, same convention as slot generation.
        var rows = (List<Tuple>) entityManager.createNativeQuery("""
                SELECT (date_trunc('day', start_time AT TIME ZONE 'Asia/Thimphu'))::date AS day, COUNT(*) AS cnt
                FROM appointment
                WHERE start_time >= :from AND start_time < :to
                GROUP BY day
                ORDER BY day
                """, Tuple.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows.stream()
                .map(row -> new DayCount(toLocalDate(row.get("day")), ((Number) row.get("cnt")).longValue()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<DoctorDayCount> countByDoctorAndDay(Instant from, Instant to) {
        // See docs/designs/jdwnrh-scheduler.md, "Scope Expansion" -> E5: the doctor/department
        // heatmap is booking LOAD by doctor-by-day (this exact GROUP BY), not true free-capacity
        // availability — a real availability figure would mean re-running slot generation against
        // every doctor's schedule for the whole range, which isn't a GROUP BY aggregate at all.
        var rows = (List<Tuple>) entityManager.createNativeQuery("""
                SELECT doctor_id, (date_trunc('day', start_time AT TIME ZONE 'Asia/Thimphu'))::date AS day, COUNT(*) AS cnt
                FROM appointment
                WHERE start_time >= :from AND start_time < :to
                GROUP BY doctor_id, day
                ORDER BY day, doctor_id
                """, Tuple.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows.stream()
                .map(row -> new DoctorDayCount((UUID) row.get("doctor_id"), toLocalDate(row.get("day")), ((Number) row.get("cnt")).longValue()))
                .toList();
    }

    // Modern pgjdbc + Hibernate maps a native `date` column straight to java.time.LocalDate,
    // not java.sql.Date — only found by actually running this query, not by reading the code.
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        throw new IllegalStateException("Unexpected date type: " + value.getClass());
    }

    public record StatusCount(String status, long count) {
    }

    public record DoctorCount(UUID doctorId, long count) {
    }

    public record DayCount(LocalDate day, long count) {
    }

    public record DoctorDayCount(UUID doctorId, LocalDate day, long count) {
    }
}
