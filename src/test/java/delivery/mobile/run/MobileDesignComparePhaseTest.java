package delivery.mobile.run;

import delivery.portal.service.DesignReferenceService;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * Smoke-test the mobile Figma comparator end-to-end without a live vision client.
 * A null client makes {@code DesignCompareGate} return UNCERTAIN — that's still enough to verify
 * the comparator wiring (reads step-NNN.png + per-TC Figma, writes design-compare*.json beside it).
 */
public class MobileDesignComparePhaseTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    public void writesTcAndStepEvidenceWhenFigmasExist() throws IOException {
        Path tmp = Files.createTempDirectory("mobile-fv-");
        Path projectRoot = tmp.resolve("project");
        Path evidenceRoot = tmp.resolve("mobile-runs/mrun_test/evidence");
        Path tcEvidence = evidenceRoot.resolve("TC_42");
        Files.createDirectories(projectRoot);
        Files.createDirectories(tcEvidence);

        DesignReferenceService references = new DesignReferenceService();
        references.upload(projectRoot, "TC_42", TINY_PNG, "image/png");          // whole-TC Figma
        references.uploadStep(projectRoot, "TC_42", 3, TINY_PNG, "image/png");    // per-step Figma

        // Runner-produced step screenshot + a final one so pickActualScreenshot finds something.
        Files.write(tcEvidence.resolve("step-003.png"), TINY_PNG);
        Files.write(tcEvidence.resolve("step-005.png"), TINY_PNG);

        MobileDesignComparePhase phase =
                new MobileDesignComparePhase(projectRoot, evidenceRoot, null);
        phase.run(List.of("TC_42"));

        // TC-level evidence written.
        Path tcJson = tcEvidence.resolve("design-compare.json");
        Assert.assertTrue(Files.isRegularFile(tcJson), tcJson.toString());
        JSONObject tcBody = new JSONObject(Files.readString(tcJson));
        Assert.assertEquals(tcBody.getString("tcId"), "TC_42");
        Assert.assertEquals(tcBody.getString("scope"), "tc");

        // Per-step evidence written for step 3 (where both Figma and screenshot exist).
        Path stepJson = tcEvidence.resolve("design-compare-step-003.json");
        Assert.assertTrue(Files.isRegularFile(stepJson), stepJson.toString());
        JSONObject stepBody = new JSONObject(Files.readString(stepJson));
        Assert.assertEquals(stepBody.getInt("stepIndex"), 3);
        Assert.assertEquals(stepBody.getString("scope"), "step");
    }

    @Test
    public void skipsCleanlyWhenNothingUploaded() throws IOException {
        Path tmp = Files.createTempDirectory("mobile-fv-empty-");
        Path projectRoot = tmp.resolve("project");
        Path evidenceRoot = tmp.resolve("mobile-runs/mrun_empty/evidence");
        Files.createDirectories(projectRoot);
        Files.createDirectories(evidenceRoot);
        // No uploads, no screenshots.

        MobileDesignComparePhase phase =
                new MobileDesignComparePhase(projectRoot, evidenceRoot, null);
        phase.run(List.of("TC_01"));

        // No evidence files produced.
        try (var walk = Files.walk(evidenceRoot)) {
            long files = walk.filter(Files::isRegularFile).count();
            Assert.assertEquals(files, 0L, "No Figma + no screenshot → no evidence files expected");
        }
    }
}
