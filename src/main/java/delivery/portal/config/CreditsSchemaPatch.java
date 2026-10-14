package delivery.portal.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures portal_users.approval_* and credit tables are usable on H2/MSSQL with ddl-auto=update.
 * Existing accounts without a status are treated as APPROVED (no lockout). New signups set PENDING_APPROVAL in code.
 * Does <b>not</b> grant credits.
 */
@Component
public class CreditsSchemaPatch {
    private final JdbcTemplate jdbc;

    public CreditsSchemaPatch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void patch() {
        try {
            jdbc.execute("ALTER TABLE portal_users ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32) DEFAULT 'APPROVED'");
        } catch (Exception ignored) {
            // dialect may not support IF NOT EXISTS — ignore; Hibernate ddl-auto also creates
        }
        try {
            jdbc.execute("ALTER TABLE portal_users ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP");
        } catch (Exception ignored) {
        }
        try {
            jdbc.execute("ALTER TABLE portal_users ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT");
        } catch (Exception ignored) {
        }
        try {
            jdbc.update("UPDATE portal_users SET approval_status = 'APPROVED' WHERE approval_status IS NULL");
        } catch (Exception ignored) {
        }
    }
}
