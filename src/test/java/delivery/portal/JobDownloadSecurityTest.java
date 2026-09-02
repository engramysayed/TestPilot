package delivery.portal;

import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.DryRunConversionService;
import delivery.job.JobProgressTracker;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * T062: download package must never contain job password / filled secrets.
 */
public class JobDownloadSecurityTest {

    @Test
    public void dryRunZip_neverContainsPasswordOrFilledWebappProperties() throws Exception {
        Path temp = Files.createTempDirectory("portal-sec");
        Path store = temp.resolve("store");
        Path work = temp.resolve("work");
        String secretPassword = "SuperSecretPortalPass-9x!";

        ConversionJobRequest request = new ConversionJobRequest(
                "prj_security",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://example.com",
                "demo-user",
                secretPassword,
                work,
                store,
                Path.of("customer-framework-template"),
                "NEW",
                "http://127.0.0.1:11434",
                "qwen2.5:latest"
        );

        ConversionJobResult result = new DryRunConversionService().run(request, new JobProgressTracker());
        Assert.assertTrue(Files.isRegularFile(result.zipFile()));

        boolean sawFilledWebapp = false;
        try (ZipFile zip = new ZipFile(result.zipFile().toFile())) {
            Assert.assertNotNull(zip.getEntry("src/main/java/project/drivers/WebDriverFactory.java"),
                    "ZIP must include TAF WebDriverFactory");
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.endsWith("webapp.properties") && !name.endsWith(".example")) {
                    sawFilledWebapp = true;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Assert.assertFalse(
                            content.contains(secretPassword),
                            "ZIP entry must not contain job password: " + name
                    );
                }
            }
        }
        Assert.assertFalse(sawFilledWebapp, "ZIP must not include filled webapp.properties");
    }
}
