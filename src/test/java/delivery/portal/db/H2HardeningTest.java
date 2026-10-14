package delivery.portal.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Issue #2: H2 hardening (B01). */
public class H2HardeningTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    private static Properties appProps() throws Exception {
        Properties p = new Properties();
        try (InputStream in = H2HardeningTest.class.getResourceAsStream("/application.properties")) {
            p.load(in);
        }
        return p;
    }

    @Test
    public void shippedUrlHasNoAutoServerAndPasswordComesFromEnv() throws Exception {
        Properties p = appProps();
        String url = p.getProperty("spring.datasource.url");
        Assert.assertTrue(url.startsWith("jdbc:h2:file:"), url);
        Assert.assertFalse(url.toUpperCase().contains("AUTO_SERVER"), url);
        Assert.assertEquals(p.getProperty("spring.datasource.password"), "${KEEL_DB_PASSWORD:}");
        Assert.assertEquals(p.getProperty("spring.h2.console.enabled"), "false");
    }

    @Test
    public void checkRules() {
        Assert.assertFalse(H2DatasourceSafety.check(true, "jdbc:h2:file:./x", "").ok());
        Assert.assertFalse(H2DatasourceSafety.check(true, "jdbc:h2:file:./x;AUTO_SERVER=TRUE", "pw").ok());
        Assert.assertTrue(H2DatasourceSafety.check(true, "jdbc:h2:file:./x", "pw").ok());
        H2DatasourceSafety.Result dev = H2DatasourceSafety.check(false, "jdbc:h2:file:./x;AUTO_SERVER=TRUE", "");
        Assert.assertTrue(dev.ok());
        Assert.assertEquals(dev.warnings().size(), 2);
        Assert.assertTrue(H2DatasourceSafety.check(false, "jdbc:h2:mem:t", "").warnings().isEmpty());
        Assert.assertTrue(H2DatasourceSafety.check(true, "jdbc:sqlserver://x", "").ok());
    }

    private static ConfigurableApplicationContext start(String profile, String password) {
        SpringApplication app = new SpringApplication(Empty.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        return app.run("--spring.profiles.active=" + profile,
                "--spring.datasource.url=jdbc:h2:file:./target/never-created/portal-db",
                "--spring.datasource.password=" + password);
    }

    @Test
    public void prodProfileRefusesToStartWithEmptyPassword() {
        IllegalStateException e = Assert.expectThrows(IllegalStateException.class, () -> start("prod", ""));
        Assert.assertTrue(e.getMessage().contains("INSECURE_DATASOURCE"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("KEEL_DB_PASSWORD"), e.getMessage());
    }

    @Test
    public void devProfileStartsWithWarningAndProdStartsWithPassword() {
        try (ConfigurableApplicationContext c = start("dev", "")) {
            Assert.assertTrue(c.isActive());
        }
        try (ConfigurableApplicationContext c = start("prod", "s3cret")) {
            Assert.assertTrue(c.isActive());
        }
    }

    /**
     * With AUTO_SERVER, H2 writes {@code server=host:port} into the .lock.db file and listens on it.
     * Without it, no server entry exists, i.e. no H2 TCP port is opened. The positive control proves the
     * check would detect a listener.
     */
    @Test
    public void shippedUrlOpensNoH2TcpPort() throws Exception {
        Path dir = Files.createTempDirectory("h2-hard");
        String shipped = appProps().getProperty("spring.datasource.url")
                .replace("./delivery-store/portal-db", dir.resolve("portal-db").toString().replace('\\', '/'));
        try (Connection c = DriverManager.getConnection(shipped, "sa", "pw")) {
            Path lockFile = dir.resolve("portal-db.lock.db");
            // MVStore without AUTO_SERVER uses an OS file lock and writes no server=host:port entry
            if (Files.exists(lockFile)) {
                String lock = Files.readString(lockFile, StandardCharsets.ISO_8859_1);
                Assert.assertFalse(lock.contains("server="), "H2 published a TCP server: " + lock);
            }
        }
        Path dir2 = Files.createTempDirectory("h2-auto");
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:file:" + dir2.resolve("db").toString().replace('\\', '/') + ";AUTO_SERVER=TRUE", "sa", "pw")) {
            String lock = Files.readString(dir2.resolve("db.lock.db"), StandardCharsets.ISO_8859_1);
            Assert.assertTrue(lock.contains("server="), "positive control: AUTO_SERVER must publish a port");
        }
    }

    @Test
    public void migratesLegacyEmptyPasswordOnceAndIsIdempotent() throws Exception {
        Path dir = Files.createTempDirectory("h2-mig");
        String url = "jdbc:h2:file:" + dir.resolve("portal-db").toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            c.createStatement().execute("CREATE TABLE t(id INT)");
        }
        Assert.assertEquals(H2PasswordMigrator.migrate(url, "sa", "n3w'pw"), H2PasswordMigrator.Outcome.MIGRATED);
        try (Connection c = DriverManager.getConnection(url, "sa", "n3w'pw")) {
            c.createStatement().execute("SELECT * FROM t");
        }
        Assert.expectThrows(SQLException.class, () -> DriverManager.getConnection(url, "sa", "").close());
        Assert.assertEquals(H2PasswordMigrator.migrate(url, "sa", "n3w'pw"), H2PasswordMigrator.Outcome.ALREADY_SET);
        Assert.assertEquals(H2PasswordMigrator.migrate(url, "sa", "other"), H2PasswordMigrator.Outcome.UNKNOWN_PASSWORD);
        // fresh install: nothing to migrate, no DB file created
        Path fresh = Files.createTempDirectory("h2-fresh");
        Assert.assertEquals(H2PasswordMigrator.migrate("jdbc:h2:file:" + fresh.resolve("db").toString().replace('\\', '/'),
                "sa", "pw"), H2PasswordMigrator.Outcome.NOT_APPLICABLE);
        Assert.assertFalse(Files.exists(fresh.resolve("db.mv.db")));
        Assert.assertEquals(H2PasswordMigrator.migrate("jdbc:h2:mem:x", "sa", "pw"), H2PasswordMigrator.Outcome.NOT_APPLICABLE);
    }
}
