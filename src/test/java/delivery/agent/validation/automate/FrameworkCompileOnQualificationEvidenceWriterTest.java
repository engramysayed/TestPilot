package delivery.agent.validation.automate;

import delivery.agent.validation.contract.FrameworkExpectedInventory;
import delivery.agent.validation.contract.ValidationContractIds;
import delivery.agent.validation.contract.ValidationReport;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class FrameworkCompileOnQualificationEvidenceWriterTest {

    @Test
    public void writeEvidence_beforeAssertMismatch_persistsCompileWithoutAcceptance() throws Exception {
        Path work = Files.createTempDirectory("evidence-writer-");
        Path project = work.resolve("project");
        Path repair = work.resolve("framework-acceptance/repair-candidate");
        Files.createDirectories(repair);
        Files.writeString(work.resolve("framework-acceptance/emit-compile-check.ok"), repair.toString());
        var inventory = FrameworkExpectedInventory.fromOutcomes(java.util.List.of());
        var pre = ValidationReport.of(ValidationContractIds.FRAMEWORK_CONTRACT_V1, java.util.List.of());
        var outcome = AutomateFrameworkAcceptanceOutcome.rejected(
                AutomateFrameworkAcceptanceStatus.REVALIDATION_FAILED,
                pre,
                null,
                "task-fake",
                "revalidation");
        FrameworkCompileOnQualificationEvidenceWriter.write(
                work,
                "EVIDENCE_FIXTURE",
                "compile-live-EVIDENCE_FIXTURE",
                outcome,
                pre,
                inventory,
                true,
                repair.resolve("docs/EMIT_COMPILE_FAIL.log"),
                project,
                repair);
        Path dest = Path.of("docs/handoff/phase6-8-compile-on-EVIDENCE_FIXTURE.json");
        Assert.assertTrue(Files.isRegularFile(dest), dest.toAbsolutePath().toString());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(dest));
        Assert.assertEquals(json.path("compilePassed").asBoolean(), true);
        Assert.assertEquals(json.path("accepted").asBoolean(), false);
        Assert.assertEquals(json.path("gateStatus").asText(), "REVALIDATION_FAILED");
        Assert.assertEquals(json.path("failureClass").asText(), "REPAIR_QUALITY_OR_VALIDATION");
        Files.deleteIfExists(dest);
    }
}