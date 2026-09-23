package delivery.packager;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FrameworkPackagerTest {
    @Test
    public void copyTemplate_keepsExampleProperties() throws Exception {
        Path temp = Files.createTempDirectory("pack");
        Path dest = temp.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        packager.copyTemplate(Path.of("customer-framework-template"), dest);
        Assert.assertTrue(Files.exists(dest.resolve("src/main/resources/webapp.properties.example")));
        Assert.assertFalse(Files.exists(dest.resolve("src/main/resources/webapp.properties")));
        Assert.assertTrue(Files.exists(dest.resolve("src/main/java/project/drivers/WebDriverFactory.java")));
        Assert.assertFalse(Files.exists(dest.resolve("templates/PageClass.java.ftl")));
        Assert.assertFalse(Files.exists(dest.resolve("src/test/java/project/tests/LoginTest.java")),
                "sample LoginTest must not ship in customer package");
        packager.writeScoreReport(dest, List.of(new TcOutcome("TC_001", TcStatus.PASSED, List.of(), "", null)));
        Assert.assertTrue(Files.exists(dest.resolve("docs/AUTOMATION_SCORE.md")));
        Path zip = temp.resolve("out.zip");
        packager.zip(dest, zip);
        Assert.assertTrue(Files.exists(zip));
        // Ensure zip does not contain LoginTest even if file sneaks onto disk
        Files.createDirectories(dest.resolve("src/test/java/project/tests"));
        Files.writeString(dest.resolve("src/test/java/project/tests/LoginTest.java"), "// sample");
        Path zip2 = temp.resolve("out2.zip");
        packager.zip(dest, zip2);
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip2.toFile())) {
            Assert.assertNull(zf.getEntry("src/test/java/project/tests/LoginTest.java"));
        }
    }

    @Test
    public void copyAndZipExcludePriorRunArtifacts() throws Exception {
        Path temp = Files.createTempDirectory("pack-allowlist");
        Path seed = temp.resolve("seed");
        Files.createDirectories(seed.resolve("src/main/java/project/drivers"));
        Files.writeString(seed.resolve("pom.xml"), "<project></project>");
        Files.writeString(seed.resolve("src/main/java/project/drivers/WebDriverFactory.java"), "class WebDriverFactory {}");
        Files.createDirectories(seed.resolve("test-output/Logs"));
        Files.writeString(seed.resolve("test-output/Logs/previous-run.log"), "REVIEW_OLD_RUN_SENTINEL");
        Files.createDirectories(seed.resolve("target/classes"));
        Files.writeString(seed.resolve("target/classes/Stale.class"), "stale");

        Path dest = temp.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        packager.copyTemplate(seed, dest);
        Assert.assertFalse(Files.exists(dest.resolve("test-output/Logs/previous-run.log")),
                "copyTemplate must not ship prior test-output logs");
        Assert.assertFalse(Files.exists(dest.resolve("target/classes/Stale.class")));
        Assert.assertTrue(Files.exists(dest.resolve("pom.xml")));
        Assert.assertTrue(Files.exists(dest.resolve("src/main/java/project/drivers/WebDriverFactory.java")));

        Files.createDirectories(dest.resolve("test-output/Logs"));
        Files.writeString(dest.resolve("test-output/Logs/sneaked.log"), "should-not-zip");
        Path zip = temp.resolve("out.zip");
        packager.zip(dest, zip);
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            Assert.assertNull(zf.getEntry("test-output/Logs/previous-run.log"));
            Assert.assertNull(zf.getEntry("test-output/Logs/sneaked.log"),
                    "zip must exclude runtime output even if it appears on disk");
            Assert.assertNotNull(zf.getEntry("pom.xml"));
        }
    }

    @Test
    public void copyAndZipExcludeInternalValidationSelfTests() throws Exception {
        Path temp = Files.createTempDirectory("pack-selftests");
        Path dest = temp.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        packager.copyTemplate(Path.of("customer-framework-template"), dest);
        Assert.assertFalse(
                Files.exists(dest.resolve("src/test/java/project/validations/ValidationAssertAllTest.java")),
                "customer packages must not ship internal ValidationAssertAllTest");
        Assert.assertFalse(Files.exists(dest.resolve(
                "src/test/java/project/validations/support/FalseCustomerAssertionSample.java")));
        Path zip = temp.resolve("out.zip");
        packager.zip(dest, zip);
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            Assert.assertNull(zf.getEntry("src/test/java/project/validations/ValidationAssertAllTest.java"));
            Assert.assertNull(zf.getEntry(
                    "src/test/java/project/validations/support/QuitProbeDriverFactory.java"));
        }
        Files.createDirectories(dest.resolve("src/test/java/project/validations"));
        Files.writeString(dest.resolve("src/test/java/project/validations/ValidationAssertAllTest.java"), "// sneaked");
        Path zip2 = temp.resolve("out2.zip");
        packager.zip(dest, zip2);
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip2.toFile())) {
            Assert.assertNull(zf.getEntry("src/test/java/project/validations/ValidationAssertAllTest.java"),
                    "zip must drop sneaked internal helper tests");
        }
    }

    @Test
    public void overlayCustomerConfigPreservesWebappProperties() throws Exception {
        Path temp = Files.createTempDirectory("overlay-config");
        Path previous = temp.resolve("previous");
        Path dest = temp.resolve("dest");
        Path config = previous.resolve("src/test/resources/config");
        Files.createDirectories(config);
        Files.writeString(config.resolve("webapp.properties"), "BASE_WEB=https://customer.example");
        Files.writeString(config.resolve("webapp.properties.example"), "BASE_WEB=");
        Files.createDirectories(dest);
        new FrameworkPackager().overlayCustomerConfig(previous, dest);
        Path copied = dest.resolve("src/test/resources/config/webapp.properties");
        Assert.assertTrue(Files.isRegularFile(copied));
        Assert.assertEquals(Files.readString(copied).trim(), "BASE_WEB=https://customer.example");
        Assert.assertFalse(Files.exists(dest.resolve("src/test/resources/config/webapp.properties.example")));
    }
}
