package delivery.mobile.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public class MobileFrameworkPackagerTest {

    @Test
    public void zipStripsHelperTestsSecretsApkAndWebDeps() throws Exception {
        Path temp = Files.createTempDirectory("m4-04-zip");
        Path project = temp.resolve("project");
        Files.createDirectories(project.resolve("src/test/java/project/runtime/settle"));
        Files.createDirectories(project.resolve("src/test/java/project/tests/generated"));
        Files.createDirectories(project.resolve("src/test/java/project/tests/todo"));
        Files.createDirectories(project.resolve("src/test/resources/config"));
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("pom.xml"), "<project></project>");
        Files.writeString(project.resolve("pom-helper-tests.xml"), "<project>helper</project>");
        Files.writeString(project.resolve("src/test/java/project/runtime/settle/SettleAfterTableParityTest.java"),
                "class SettleAfterTableParityTest {}");
        Files.writeString(project.resolve("src/test/java/project/tests/generated/TC_01.java"), "class TC_01 {}");
        Files.writeString(project.resolve("src/test/java/project/tests/todo/TC_DUMPTodo.java"), "class TC_DUMPTodo {}");
        Files.writeString(project.resolve("src/test/resources/config/appium.properties"), "mobile.udid=emulator-5554");
        Files.writeString(project.resolve("src/test/resources/config/secrets.properties"), "auth.password=CANARY_SECRET");
        Files.writeString(project.resolve("src/test/resources/config/secrets.example.properties"), "# example");
        Files.writeString(project.resolve("app.apk"), "APKBYTES");
        Files.createDirectories(project.resolve("src/main/java/project/drivers"));
        Files.writeString(project.resolve("src/main/java/project/drivers/WebDriverFactory.java"), "class Web {}");

        Path zip = temp.resolve("out.zip");
        MobileFrameworkPackager packager = new MobileFrameworkPackager();
        packager.zip(project, zip);

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Assert.assertNull(zf.getEntry("pom-helper-tests.xml"));
            Assert.assertNull(zf.getEntry("src/test/java/project/runtime/settle/SettleAfterTableParityTest.java"));
            Assert.assertNull(zf.getEntry("src/test/resources/config/secrets.properties"));
            Assert.assertNull(zf.getEntry("app.apk"));
            Assert.assertNull(zf.getEntry("src/main/java/project/drivers/WebDriverFactory.java"));
            Assert.assertNotNull(zf.getEntry("pom.xml"));
            Assert.assertNotNull(zf.getEntry("src/test/java/project/tests/generated/TC_01.java"));
            Assert.assertNotNull(zf.getEntry("src/test/java/project/tests/todo/TC_DUMPTodo.java"),
                    "TODO coverage must ship");
            Assert.assertNotNull(zf.getEntry("src/test/resources/config/appium.properties"));
            Assert.assertNotNull(zf.getEntry("src/test/resources/config/secrets.example.properties"));
        }
        String zipText = String.join("\n", MobileFrameworkPackager.listZipEntries(zip));
        Assert.assertFalse(zipText.contains("CANARY_SECRET"));
        Assert.assertFalse(zipText.contains("java-client"), zipText);
    }

    @Test
    public void configOverlayPreservedAndSecretsSkipped() throws Exception {
        Path temp = Files.createTempDirectory("m4-04-overlay");
        Path prev = temp.resolve("prev");
        Path dest = temp.resolve("dest");
        Files.createDirectories(prev.resolve("src/test/resources/config"));
        Files.createDirectories(dest.resolve("src/test/resources/config"));
        Files.writeString(prev.resolve("src/test/resources/config/appium.properties"), "mobile.udid=CUSTOM");
        Files.writeString(prev.resolve("src/test/resources/config/secrets.properties"), "auth.password=NOPE");
        Files.writeString(dest.resolve("src/test/resources/config/appium.properties"), "mobile.udid=");
        new MobileFrameworkPackager().overlayCustomerConfig(prev, dest);
        Assert.assertEquals(
                Files.readString(dest.resolve("src/test/resources/config/appium.properties")).trim(),
                "mobile.udid=CUSTOM");
        Assert.assertFalse(Files.exists(dest.resolve("src/test/resources/config/secrets.properties")));
    }

    @Test
    public void failedCompilePreservesPriorZipBytes() throws Exception {
        Path temp = Files.createTempDirectory("m4-04-compile");
        Path prior = temp.resolve("prior.zip");
        byte[] original = "PRIOR_BYTES_CANARY".getBytes();
        Files.write(prior, original);
        Path project = temp.resolve("proj");
        Files.createDirectories(project);
        // no pom → structural fail
        MobileCompileGate.Result r = new MobileFrameworkPackager()
                .compileAndZip(project, temp.resolve("new.zip"), prior, false);
        Assert.assertFalse(r.ok());
        Assert.assertFalse(Files.exists(temp.resolve("new.zip")));
        Assert.assertEquals(Files.readAllBytes(prior), original);
    }

    @Test
    public void obsoleteGeneratedFilesRemovedByWriter() throws Exception {
        // Covered by MobileCodeWriter deleteObsolete — smoke: packager does not reintroduce helper tests
        Path temp = Files.createTempDirectory("m4-04-obsolete");
        Path seed = temp.resolve("seed");
        Path dest = temp.resolve("dest");
        Files.createDirectories(seed.resolve("src/test/java/project/config"));
        Files.writeString(seed.resolve("pom.xml"), "<project/>");
        Files.writeString(seed.resolve("src/test/java/project/config/MobilePropertiesValidationTest.java"), "class T {}");
        new MobileFrameworkPackager().copyTemplate(seed, dest);
        Assert.assertFalse(Files.exists(
                dest.resolve("src/test/java/project/config/MobilePropertiesValidationTest.java")));
        Assert.assertTrue(Files.exists(dest.resolve("pom.xml")));
    }

    @Test
    public void dispatchRefusesWebAcceptsAndroid() {
        Assert.assertTrue(MobileEmitDispatch.acceptsPlatform("ANDROID"));
        Assert.assertFalse(MobileEmitDispatch.acceptsPlatform("WEB"));
        try {
            MobileEmitDispatch.refuseWeb("WEB");
            Assert.fail("expected refuse");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("WEB"));
        }
        MobileEmitDispatch.refuseWeb("ANDROID");
    }
}
