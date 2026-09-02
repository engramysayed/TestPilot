package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class DryRunConversionServiceTest {
    @Test
    public void dryRun_packagesAllCasesAsTodo() throws Exception {
        Path temp = Files.createTempDirectory("dry");
        ConversionJobRequest request = new ConversionJobRequest(
                "prj_dry",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://example.com",
                "",
                "",
                temp.resolve("work"),
                temp.resolve("store"),
                Path.of("customer-framework-template"),
                "NEW",
                "http://127.0.0.1:11434",
                "qwen2.5:latest"
        );
        ConversionJobResult result = new DryRunConversionService().run(request, new JobProgressTracker());
        Assert.assertEquals(result.passed(), 0);
        Assert.assertTrue(result.todo() > 0);
        Assert.assertTrue(Files.isRegularFile(result.zipFile()));
        Assert.assertTrue(Files.isRegularFile(result.scoreReport()));
    }
}
