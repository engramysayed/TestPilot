package delivery.mobile.codegen.harness;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileIrCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MobileNewUpdateHarnessTest {

    @Test
    public void newUpdateHarnessPackagesAndVerifiesShaWithoutDevice() throws Exception {
        Path work = Files.createTempDirectory("m4-06-harness");
        MobileCaseIr neu = load("fixtures/login-case.json");
        MobileCaseIr upd = load("fixtures/secret-case.json");
        MobileNewUpdateHarness harness = new MobileNewUpdateHarness(null);
        var report = harness.run(work, List.of(neu), List.of(neu, upd), false);
        Assert.assertTrue(report.neu().ok(), report.neu().message());
        Assert.assertTrue(report.update().ok(), report.update().message());
        Assert.assertTrue(report.casesMatched());
        Assert.assertTrue(report.shaVerified());
        Assert.assertTrue(Files.isDirectory(work.resolve("new-unzip")));
        Assert.assertTrue(Files.isDirectory(work.resolve("update-unzip")));
        Assert.assertFalse(report.deviceGatedSteps().isEmpty());
        Assert.assertTrue(report.deviceGatedSteps().get(0).startsWith("DEVICE-GATED"));
        Assert.assertFalse(report.neu().message().toLowerCase().contains("device pass"));
    }

    private static MobileCaseIr load(String resource) throws Exception {
        try (var in = MobileNewUpdateHarnessTest.class.getClassLoader().getResourceAsStream(resource)) {
            Assert.assertNotNull(in, resource);
            return MobileIrCodec.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
