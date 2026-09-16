package delivery.portal.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * H2 ddl-auto cannot add NOT NULL columns onto a table with existing rows.
 * Ensure progress/message columns exist with safe defaults.
 */
@Component
public class JobsSchemaPatch implements ApplicationRunner {
    private static final Logger log = LogManager.getLogger(JobsSchemaPatch.class);
    private final JdbcTemplate jdbc;

    public JobsSchemaPatch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnQuietly("progress_current", "INT DEFAULT 0 NOT NULL");
        addColumnQuietly("progress_total", "INT DEFAULT 0 NOT NULL");
        addColumnQuietly("message", "VARCHAR(1024)");
        addColumnQuietly("job_kind", "VARCHAR(16) DEFAULT 'CONVERT'");
        addColumnQuietly("tenant_id", "VARCHAR(40)");
        addColumnQuietly("attempt_id", "VARCHAR(64)");
        addColumnQuietly("worker_id", "VARCHAR(64)");
        addColumnQuietly("lease_until", "TIMESTAMP");
        addColumnQuietly("cancel_generation", "INT DEFAULT 0 NOT NULL");
        addColumnQuietly("claim_stage", "VARCHAR(16)");
        addColumnQuietly("input_snapshot_hash", "VARCHAR(64)");
        addColumnQuietly("provider_allowlist_snapshot", "VARCHAR(128)");
        addColumnQuietly("library_revision_id", "VARCHAR(64)");
        addColumnQuietly("precision_max_snapshot", "INT DEFAULT 0 NOT NULL");
        try {
            jdbc.update("UPDATE jobs SET progress_current = 0 WHERE progress_current IS NULL");
            jdbc.update("UPDATE jobs SET progress_total = 0 WHERE progress_total IS NULL");
            jdbc.update("UPDATE jobs SET job_kind = 'CONVERT' WHERE job_kind IS NULL OR job_kind = ''");
        } catch (Exception e) {
            log.debug("jobs progress null backfill skipped: {}", e.getMessage());
        }
    }

    private void addColumnQuietly(String column, String typeSql) {
        try {
            jdbc.execute("ALTER TABLE jobs ADD COLUMN " + column + " " + typeSql);
            log.info("Added jobs.{} column", column);
        } catch (Exception e) {
            // Column already exists, or DB dialect variant — safe to ignore
            log.debug("jobs.{} ensure skipped: {}", column, e.getMessage());
        }
    }
}
