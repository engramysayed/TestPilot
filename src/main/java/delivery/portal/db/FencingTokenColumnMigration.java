package delivery.portal.db;

import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * B08 migration: adds {@code jobs.fencing_token BIGINT} to an existing database before JPA starts, so it also
 * works with {@code spring.jpa.hibernate.ddl-auto=validate|none} (Postgres / SQL Server installs). Idempotent;
 * a fresh database without a {@code jobs} table is left to Hibernate. Existing rows read as 0 (never claimed
 * over v2), which is what the fencing check expects. SQL kept in {@code db/migration/B08__jobs_fencing_token.sql}.
 */
@Component("fencingTokenColumnMigration")
public class FencingTokenColumnMigration {
    public static final String SQL = "ALTER TABLE jobs ADD fencing_token BIGINT";
    private final boolean applied;

    public FencingTokenColumnMigration(DataSource dataSource) throws Exception {
        this.applied = migrate(dataSource);
    }

    public static boolean migrate(DataSource dataSource) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            if (!exists(c, "jobs", null) || exists(c, "jobs", "fencing_token")) {
                return false;
            }
            try (Statement s = c.createStatement()) {
                s.execute(SQL);
            }
            return true;
        }
    }

    private static boolean exists(Connection c, String table, String column) throws Exception {
        for (String t : new String[]{table, table.toUpperCase()}) {
            try (ResultSet rs = column == null
                    ? c.getMetaData().getTables(null, null, t, null)
                    : c.getMetaData().getColumns(null, null, t, null)) {
                while (rs.next()) {
                    if (column == null || column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean applied() {
        return applied;
    }

    /** Makes the JPA EntityManagerFactory wait for this migration. */
    @Component
    static class JpaWaitsForFencingMigration extends EntityManagerFactoryDependsOnPostProcessor {
        JpaWaitsForFencingMigration() {
            super("fencingTokenColumnMigration");
        }
    }
}
