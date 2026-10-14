package delivery.portal.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One-time migration for existing file databases created with the old empty {@code sa} password.
 *
 * <p>Runs only when: the URL is an H2 file URL, a non-empty password is configured, login with the
 * configured password fails with "wrong user name or password" (28000), <b>and</b> login with an empty
 * password succeeds. In that case it runs {@code ALTER USER <user> SET PASSWORD '...'}. In every other
 * case it does nothing, so it is idempotent and cannot lock anyone out. A DB already holding a
 * different non-empty password is left alone.</p>
 */
public final class H2PasswordMigrator {
    private static final Logger LOG = LogManager.getLogger(H2PasswordMigrator.class);
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    static final int H2_WRONG_USER_OR_PASSWORD = 28000;

    public enum Outcome { NOT_APPLICABLE, ALREADY_SET, MIGRATED, UNKNOWN_PASSWORD, FAILED }

    private H2PasswordMigrator() {
    }

    public static Outcome migrate(String url, String user, String newPassword) {
        if (url == null || !url.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:h2:file:")
                || newPassword == null || newPassword.isEmpty()) {
            return Outcome.NOT_APPLICABLE;
        }
        String u = (user == null || user.isBlank()) ? "sa" : user.trim();
        if (!IDENT.matcher(u).matches()) {
            LOG.warn("H2 password migration skipped: unsupported user name");
            return Outcome.NOT_APPLICABLE;
        }
        // never create a new DB from here
        String probeUrl = url.contains("IFEXISTS") ? url : url + ";IFEXISTS=TRUE";
        try (Connection c = DriverManager.getConnection(probeUrl, u, newPassword)) {
            return Outcome.ALREADY_SET;
        } catch (SQLException e) {
            if (e.getErrorCode() != H2_WRONG_USER_OR_PASSWORD) {
                // missing DB (fresh install), locked by another process, etc.
                return Outcome.NOT_APPLICABLE;
            }
        }
        try (Connection c = DriverManager.getConnection(probeUrl, u, "");
             Statement st = c.createStatement()) {
            st.execute("ALTER USER " + u + " SET PASSWORD '" + newPassword.replace("'", "''") + "'");
            LOG.warn("H2 password migration: replaced the empty password for user {} (issue #2)", u);
            return Outcome.MIGRATED;
        } catch (SQLException e) {
            if (e.getErrorCode() == H2_WRONG_USER_OR_PASSWORD) {
                LOG.error("H2 password migration: DB has a password that differs from KEEL_DB_PASSWORD; not changed");
                return Outcome.UNKNOWN_PASSWORD;
            }
            LOG.error("H2 password migration failed: {}", e.getMessage());
            return Outcome.FAILED;
        }
    }
}
