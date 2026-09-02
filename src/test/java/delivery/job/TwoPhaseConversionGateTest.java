package delivery.job;

import delivery.codegen.PageClusterer;
import delivery.ir.TcDraftStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Non-browser release-gate checks for two-phase conversion (T041/T049 prerequisites).
 * Live NEW/UPDATE is site-agnostic — see docs/ops/e2e-conversion-gate.md.
 */
public class TwoPhaseConversionGateTest {

    @Test
    public void twoPhaseTypesWired() {
        Assert.assertEquals(ProvePhase.MAX_RETRIES, 2);
        Assert.assertNotNull(new ProvePhase(new JobProgressTracker()));
        Assert.assertNotNull(new EmitPhase(new JobProgressTracker()));
        Assert.assertEquals(PageClusterer.pageNameFromUrl("https://example.com/inventory.html"), "Inventory");
        Assert.assertEquals(TcDraftStore.safeFileName("TC/SD:01"), "TC_SD_01");
    }

    @Test
    public void gateRunbookPresent() {
        Assert.assertTrue(Files.isRegularFile(Path.of("docs/ops/e2e-conversion-gate.md")));
        Assert.assertTrue(Files.isRegularFile(Path.of("scripts/run-conversion-gate.ps1")));
        Assert.assertTrue(Files.isRegularFile(Path.of("specs/003-two-phase-conversion/spec.md")));
    }
}
