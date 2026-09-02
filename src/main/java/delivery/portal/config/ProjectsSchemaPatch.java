package delivery.portal.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Belt-and-suspenders for {@code projects} columns when ddl-auto alter fails or was skipped.
 * Primary fix: {@code portal-schema-patch.sql} before Hibernate.
 */
@Component
public class ProjectsSchemaPatch implements ApplicationRunner {
    private static final Logger log = LogManager.getLogger(ProjectsSchemaPatch.class);
    private final JdbcTemplate jdbc;

    public ProjectsSchemaPatch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnQuietly("archived", "BOOLEAN DEFAULT FALSE");
        addColumnQuietly("base_url", "VARCHAR(2048)");
        addColumnQuietly("archived_at", "TIMESTAMP");
        try {
            jdbc.update("UPDATE projects SET archived = FALSE WHERE archived IS NULL");
        } catch (Exception e) {
            log.debug("projects.archived backfill skipped: {}", e.getMessage());
        }
    }

    private void addColumnQuietly(String column, String typeSql) {
        try {
            jdbc.execute("ALTER TABLE projects ADD COLUMN " + column + " " + typeSql);
            log.info("Added projects.{} column", column);
        } catch (Exception e) {
            log.debug("projects.{} ensure skipped: {}", column, e.getMessage());
        }
    }
}
