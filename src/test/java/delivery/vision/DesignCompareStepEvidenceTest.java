package delivery.vision;

import delivery.portal.engine.InProcessEnginePorts;
import delivery.portal.service.DesignReferenceService;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Per-step Figma Validate wiring: file layout, port lookups, and step-scoped evidence.
 */
public class DesignCompareStepEvidenceTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    public void portResolvesStepRefAndScreenshotAtExpectedPaths() throws IOException {
        Path tmp = Files.createTempDirectory("figma-step-port-");
        Path projectRoot = tmp.resolve("project");
        Path evidenceDir = tmp.resolve("evidence/TC_1");
        Files.createDirectories(projectRoot);
        Files.createDirectories(evidenceDir);

        DesignReferenceService references = new DesignReferenceService();
        references.uploadStep(projectRoot, "TC_1", 2, TINY_PNG, "image/png");
        Files.write(evidenceDir.resolve("step-002.png"), TINY_PNG);

        InProcessEnginePorts port = new InProcessEnginePorts();
        Assert.assertEquals(port.designReferenceSteps(projectRoot, "TC_1"), List.of(2));
        Optional<Path> ref = port.designReferenceStep(projectRoot, "TC_1", 2);
        Assert.assertTrue(ref.isPresent());
        Assert.assertEquals(ref.get(),
                projectRoot.resolve("design-references/TC_1/step-002.png"));

        Assert.assertTrue(port.stepScreenshot(evidenceDir, 2).isPresent());
        Assert.assertTrue(port.stepScreenshot(evidenceDir, 9).isEmpty());
        Assert.assertTrue(port.designReferenceStep(projectRoot, "TC_1", 9).isEmpty());
    }

    @Test
    public void writeStepUsesScopedFilenamesAndLinksToStepReference() throws IOException {
        Path evidenceDir = Files.createTempDirectory("figma-step-ev-");
        DesignCompareResult result = DesignCompareResult.uncertain("try it");
        DesignCompareEvidence.writeStep(evidenceDir, "TC_9", 7, result,
                null, TINY_PNG, "cursor", "auto");

        Assert.assertTrue(Files.isRegularFile(evidenceDir.resolve("design-actual-step-007.png")));
        Path json = evidenceDir.resolve("design-compare-step-007.json");
        Assert.assertTrue(Files.isRegularFile(json));

        JSONObject body = new JSONObject(Files.readString(json));
        Assert.assertEquals(body.getString("tcId"), "TC_9");
        Assert.assertEquals(body.getInt("stepIndex"), 7);
        Assert.assertEquals(body.getString("scope"), "step");
        Assert.assertEquals(body.getString("referenceFile"),
                "../design-references/TC_9/step-007.png");
        Assert.assertEquals(body.getString("actualFile"), "design-actual-step-007.png");
    }

    @Test
    public void whenEvidenceMissingWritesUncertainStep() throws IOException {
        Path evidenceDir = Files.createTempDirectory("figma-step-miss-");
        DesignCompareEvidence.writeStep(evidenceDir, "TC_3", 4,
                DesignCompareResult.uncertain("no screenshot for step 4"), null, null,
                "cursor", "auto");
        Path json = evidenceDir.resolve("design-compare-step-004.json");
        Assert.assertTrue(Files.isRegularFile(json));
        JSONObject body = new JSONObject(Files.readString(json));
        Assert.assertEquals(body.getString("status"),
                DesignCompareStatus.UNCERTAIN.name());
        Assert.assertTrue(body.getString("observation").toLowerCase().contains("no screenshot"));
    }
}
