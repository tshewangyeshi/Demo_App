package bt.gov.jdwnrh.scheduler.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bt.gov.jdwnrh.scheduler.config.CurrentUser;
import bt.gov.jdwnrh.scheduler.config.RlsSessionInitializer;
import bt.gov.jdwnrh.scheduler.scheduling.DoctorProfileRepository;

/** Own @Transactional boundary because RlsSessionInitializer.applyCurrentContext requires one (Propagation.MANDATORY) — see AdminReportingController. */
@Service
public class ReportingService {

    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Thimphu");

    private final RlsSessionInitializer rlsSessionInitializer;
    private final CurrentUser currentUser;
    private final ReportingRepository reportingRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public ReportingService(RlsSessionInitializer rlsSessionInitializer, CurrentUser currentUser,
                             ReportingRepository reportingRepository, DoctorProfileRepository doctorProfileRepository) {
        this.rlsSessionInitializer = rlsSessionInitializer;
        this.currentUser = currentUser;
        this.reportingRepository = reportingRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @Transactional(readOnly = true)
    public ReportSummary summarize(LocalDate fromDate, LocalDate toDate) {
        rlsSessionInitializer.applyCurrentContext(currentUser.require());

        Instant from = fromDate.atStartOfDay(HOSPITAL_ZONE).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(HOSPITAL_ZONE).toInstant();

        List<ReportingRepository.StatusCount> byStatus = reportingRepository.countByStatus(from, to);
        long total = byStatus.stream().mapToLong(ReportingRepository.StatusCount::count).sum();
        long cancelled = byStatus.stream().filter(s -> "CANCELLED".equals(s.status())).mapToLong(ReportingRepository.StatusCount::count).sum();
        long noShow = byStatus.stream().filter(s -> "NO_SHOW".equals(s.status())).mapToLong(ReportingRepository.StatusCount::count).sum();

        Map<String, Long> statusCounts = byStatus.stream()
                .collect(Collectors.toMap(ReportingRepository.StatusCount::status, ReportingRepository.StatusCount::count));

        // Doctor public profiles are cheap to fetch (small, hospital-wide list) — join in Java rather than a cross-RLS-boundary SQL JOIN.
        Map<java.util.UUID, String> doctorNames = doctorProfileRepository.findByDepartment(null).stream()
                .collect(Collectors.toMap(DoctorProfileRepository.DoctorProfile::doctorId,
                        p -> "Dr. " + p.firstName() + " " + p.lastName()));

        List<DoctorLoad> byDoctor = reportingRepository.countByDoctor(from, to).stream()
                .map(d -> new DoctorLoad(d.doctorId(), doctorNames.getOrDefault(d.doctorId(), "Unknown"), d.count()))
                .toList();

        List<DayVolume> byDay = reportingRepository.countByDay(from, to).stream()
                .map(d -> new DayVolume(d.day(), d.count()))
                .toList();

        // E5 heatmap: booking LOAD by doctor-by-day, not true free-capacity availability
        // (see ReportingRepository.countByDoctorAndDay) — reuses the same doctor-name join above.
        List<HeatmapCell> heatmap = reportingRepository.countByDoctorAndDay(from, to).stream()
                .map(c -> new HeatmapCell(c.doctorId(), doctorNames.getOrDefault(c.doctorId(), "Unknown"), c.day(), c.count()))
                .toList();

        double cancellationRate = total == 0 ? 0.0 : (double) cancelled / total;
        double noShowRate = total == 0 ? 0.0 : (double) noShow / total;

        return new ReportSummary(fromDate, toDate, total, cancellationRate, noShowRate, statusCounts, byDoctor, byDay, heatmap);
    }

    public record ReportSummary(LocalDate from, LocalDate to, long totalAppointments, double cancellationRate,
                                 double noShowRate, Map<String, Long> byStatus, List<DoctorLoad> byDoctor,
                                 List<DayVolume> byDay, List<HeatmapCell> heatmap) {
    }

    public record DoctorLoad(java.util.UUID doctorId, String doctorName, long count) {
    }

    public record DayVolume(LocalDate day, long count) {
    }

    public record HeatmapCell(java.util.UUID doctorId, String doctorName, LocalDate day, long count) {
    }
}
