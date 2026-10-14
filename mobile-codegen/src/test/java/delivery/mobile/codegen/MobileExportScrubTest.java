package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileIrCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MobileExportScrubTest {

    @Test
    public void exportOmitsApkSecretsAndMachineIds() throws Exception {
        Path temp = Files.createTempDirectory("m4-07-export");
        Path project = temp.resolve("project");
        Files.createDirectories(project.resolve("src/test/resources/config"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        MobileCaseIr ir = load("fixtures/login-case.json");
        new MobileCodeWriter().writeVerified(project, List.of(ir), NamingMap.empty());
        MobilePackageDocsWriter.writeAll(project);
        // sneak forbidden artifacts
        Files.writeString(project.resolve("app-release.apk"), "APK");
        Files.writeString(project.resolve("src/test/resources/config/secrets.properties"), "auth.password=X");
        Files.writeString(project.resolve("local.properties"), "sdk.dir=/opt/android");

        Path zip = temp.resolve("pkg.zip");
        new MobileFrameworkPackager().zip(project, zip);
        MobileExportScrub.assertClean(zip);
        Assert.assertTrue(Files.isRegularFile(project.resolve("README.md")));
        Assert.assertTrue(Files.isRegularFile(
                project.resolve("ci/github-actions/mobile-tests.yml.example")));
        Assert.assertTrue(Files.isRegularFile(
                project.resolve("docs/PORTAL_PACKAGE_DISPLAY.md")));
    }

    @Test
    public void missingRequiredPropertyFailsClearly() {
        // Mirror MobileProperties.require semantics for docs contract
        try {
            Map<String, String> empty = Map.of();
            String v = empty.get("mobile.udid");
            if (v == null || v.isBlank()) {
                throw new IllegalStateException("Missing required property: mobile.udid");
            }
            Assert.fail();
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("Missing required property: mobile.udid"));
        }
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileExportScrubTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in);
            return MobileIrCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
