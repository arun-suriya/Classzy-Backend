package com.centram.classzy.system;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Checks what this server can really verify: the app itself, the database and disk space.
 * Email and the payment gateway aren't integrated yet, so they report NOT_CONFIGURED instead of pretending to be fine.
 * A snapshot is saved every 5 minutes (kept 30 days) and shown on the System Health logs page.
 */
@Service
public class HealthService {

    public record Check(String name, String status, String detail) {
    }

    public record Snapshot(String overall, Instant checkedAt, long uptimeSeconds, int memoryUsedPct, int diskFreePct,
                           List<Check> checks) {
    }

    public record LogRow(Long id, Instant checkedAt, String overall, boolean applicationOk, boolean databaseOk,
                         Integer databaseMs, boolean storageOk, Integer memoryUsedPct, Integer diskFreePct, String detail) {
    }

    public record LogPage(List<LogRow> items, long total, int page, int size) {
    }

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final String OK = "OPERATIONAL", WARN = "DEGRADED", DOWN = "DOWN", NONE = "NOT_CONFIGURED";

    private final JdbcTemplate jdbc;
    private final HealthLogRepository logs;

    public HealthService(JdbcTemplate jdbc, HealthLogRepository logs) {
        this.jdbc = jdbc;
        this.logs = logs;
    }

    /** Runs the checks now (does not save anything). */
    public Snapshot check() {
        long uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime()).toSeconds();

        // database
        Check db;
        int dbMs = -1;
        try {
            long t0 = System.nanoTime();
            jdbc.queryForObject("select 1", Integer.class);
            dbMs = (int) ((System.nanoTime() - t0) / 1_000_000);
            db = new Check("Database", dbMs > 500 ? WARN : OK, "Responded in " + dbMs + " ms");
        } catch (RuntimeException e) {
            db = new Check("Database", DOWN, "Query failed: " + e.getClass().getSimpleName());
        }

        // disk (where uploads and logs live)
        File root = new File(".");
        long total = Math.max(root.getTotalSpace(), 1);
        int diskFree = (int) (root.getUsableSpace() * 100 / total);
        Check storage = new Check("File Storage", diskFree < 5 ? DOWN : diskFree < 10 ? WARN : OK, diskFree + "% disk space free");

        // memory
        Runtime rt = Runtime.getRuntime();
        int memUsed = (int) ((rt.totalMemory() - rt.freeMemory()) * 100 / Math.max(rt.maxMemory(), 1));
        Check app = new Check("Application", memUsed > 90 ? WARN : OK, "Up " + pretty(uptime) + ", memory " + memUsed + "% used");

        List<Check> checks = List.of(app, db,
                new Check("Payment Gateway", NONE, "No payment gateway is integrated yet"),
                new Check("Email Service", NONE, "No email provider is configured yet"),
                storage);

        String overall = checks.stream().anyMatch(c -> DOWN.equals(c.status())) ? DOWN
                : checks.stream().anyMatch(c -> WARN.equals(c.status())) ? WARN : OK;
        return new Snapshot(overall, Instant.now(), uptime, memUsed, diskFree, checks);
    }

    /** Check and save a log row. Used by the schedule and by "Run check now". */
    public Snapshot checkAndLog() {
        Snapshot s = check();
        try {
            Check db = s.checks().get(1);
            String detail = String.join(" · ", s.checks().stream().map(c -> c.name() + ": " + c.status()).toList());
            Integer ms = db.detail().startsWith("Responded in ")
                    ? Integer.valueOf(db.detail().replaceAll("\\D+", "")) : null;
            logs.save(new HealthLog(s.overall(), !DOWN.equals(s.checks().get(0).status()), !DOWN.equals(db.status()),
                    ms, !DOWN.equals(s.checks().get(4).status()), s.memoryUsedPct(), s.diskFreePct(), detail));
        } catch (RuntimeException e) {
            log.warn("Could not save health log: {}", e.getMessage());   // e.g. database down: nothing to save into
        }
        return s;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    void scheduledCheck() {
        checkAndLog();
        try {
            logs.deleteOlderThan(Instant.now().minus(Duration.ofDays(30)));
        } catch (RuntimeException e) {
            log.warn("Could not prune health logs: {}", e.getMessage());
        }
    }

    public LogPage page(int page, int size) {
        var result = logs.findAllByOrderByCheckedAtDesc(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return new LogPage(result.map(h -> new LogRow(h.getId(), h.getCheckedAt(), h.getOverall(), h.isApplicationOk(),
                h.isDatabaseOk(), h.getDatabaseMs(), h.isStorageOk(), h.getMemoryUsedPct(), h.getDiskFreePct(),
                h.getDetail())).getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private static String pretty(long seconds) {
        long d = seconds / 86400, h = seconds % 86400 / 3600, m = seconds % 3600 / 60;
        return d > 0 ? d + "d " + h + "h" : h > 0 ? h + "h " + m + "m" : m + "m";
    }
}
