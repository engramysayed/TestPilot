package delivery.agent.validation.automate;

import delivery.agent.harness.cursor.CursorSidecarClient;
import delivery.agent.validation.contract.FrameworkContractValidator;
import delivery.agent.validation.contract.ValidationReport;
import delivery.heal.ApiKeySource;
import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
/**
 * Phase 6.6 compile-on live qualification — does not disable {@code delivery.emit-compile-check}.
 *
 * {@code call api-keys.local.bat && mvn -pl keel-portal test -Dkeel.agent.cursor.live.framework=true -Dkeel.agent.cursor.compile.on=true -Dtest=FrameworkAdaptationCursorCompileOnLiveIT#missingExpectedPage_compileOnQualification}
 */
public class FrameworkAdaptationCursorCompileOnLiveIT {

    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();

    @BeforeClass
    public void assumeLive() {
        if (!Boolean.getBoolean("keel.agent.cursor.live.framework")) {
            throw new org.testng.SkipException("Set -Dkeel.agent.cursor.live.framework=true");
        }
        if (!Boolean.getBoolean("keel.agent.cursor.compile.on")) {
            throw new org.testng.SkipException("Set -Dkeel.agent.cursor.compile.on=true");
        }
        if (!ApiKeySource.isPresent(ApiKeySource.CURSOR_API_KEY)) {
            throw new org.testng.SkipException("CURSOR_API_KEY not available");
        }
        if (!EmitCompileCheckReflection.enabled()) {
            throw new org.testng.SkipException("delivery.emit-compile-check must be true");
        }
        Path repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        if (!Files.isRegularFile(repoRoot.resolve("tools/cursor-agent/agent.mjs"))) {
            throw new org.testng.SkipException("agent.mjs missing");
        }
    }

    @Test
    public void missingExpectedPage_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_LEGACY);
    }

    @Test
    public void missingExpectedTest_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.MISSING_TEST);
    }

    @Test
    public void multiPageBadImport_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.MULTI_PAGE_BAD_IMPORT);
    }

    @Test
    public void missingPageLocators_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_LOCATORS);
    }

    @Test
    public void missingPageActions_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_ACTIONS);
    }

    @Test
    public void missingPageAssertions_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_ASSERTIONS);
    }

    @Test
    public void emptyTestStub_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.EMPTY_TEST_STUB);
    }

    @Test
    public void mixedInventory_compileOnQualification() throws Exception {
        runScenario(FrameworkRepairQualificationFixtures.Scenario.MIXED_INVENTORY_BAD_IMPORT);
    }

    private void runScenario(FrameworkRepairQualificationFixtures.Scenario scenario) throws Exception {
        FrameworkRepairQualificationFixtures.Prepared prepared =
                scenario == FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_LEGACY
                        ? FrameworkRepairQualificationFixtures.prepareMissingPageLegacy()
                        : FrameworkRepairQualificationFixtures.prepare(scenario);
        Path work = prepared.workDir();
        Path project = prepared.project();
        var inventory = prepared.inventory();
        String scenarioId = scenario.name();

        ValidationReport preRepair = FrameworkContractValidator.validateWorkspace(project, TEMPLATE_ROOT, inventory);
        Assert.assertFalse(preRepair.passed());

        Path repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        String parentJob = "compile-live-" + scenarioId;
        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "cursor");
        AutomateFrameworkAcceptanceOutcome outcome;
        try {
            var gate = AutomateFrameworkAcceptanceGate.forConversionJob(
                    liveHarnessProps(repoRoot), () -> false, Duration.ofMinutes(15));
            outcome = gate.run(work, project, TEMPLATE_ROOT, work, parentJob, "proj-live", inventory);
        } finally {
            if (prevAllow == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prevAllow);
            }
        }

        Path repairCandidate = work.resolve("framework-acceptance/repair-candidate");
        Path compileOk = work.resolve("framework-acceptance/emit-compile-check.ok");
        Path compileFail = repairCandidate.resolve("docs/EMIT_COMPILE_FAIL.log");

        boolean compilePassed = outcome.status() != AutomateFrameworkAcceptanceStatus.COMPILE_FAILED
                && Files.isRegularFile(compileOk);

        FrameworkCompileOnQualificationEvidenceWriter.write(
                work, scenarioId, parentJob, outcome, preRepair, inventory, compilePassed, compileFail, project,
                repairCandidate);

        Assert.assertEquals(
                compilePassed,
                outcome.accepted(),
                "accepted outcomes must compile repair-candidate; failures must not fake compile");

        if (outcome.accepted()) {
            String compiledPath = Files.readString(compileOk).trim();
            Assert.assertTrue(compiledPath.contains("repair-candidate"),
                    "compile must target repair-candidate");
            Assert.assertEquals(
                    FrameworkRepairQualificationEvidence.treeDigestHex(project),
                    FrameworkRepairQualificationEvidence.treeDigestHex(repairCandidate),
                    "published project must match independently validated repair-candidate");
            var arch = FrameworkRepairArchitectureAssessor.assess(project, inventory);
            Assert.assertTrue(arch.functionallyComplete(),
                    "accepted compile-on repair must meet architecture quality: " + arch.findings());
        }
    }

    private static AgentHarnessProperties liveHarnessProps(Path repoRoot) {
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setAutomateFrameworkRepairEnabled(true);
        props.setMaxRuntimeMinutes(12);
        props.setCursorCommand("node " + repoRoot.resolve("tools/cursor-agent/agent.mjs").toAbsolutePath());
        return props;
    }

    /** Reflect compile flag without coupling tests to package-private gate code. */
    private static final class EmitCompileCheckReflection {
        static boolean enabled() {
            String p = System.getProperty("delivery.emit-compile-check");
            if (p == null || p.isBlank()) {
                return true;
            }
            return "true".equalsIgnoreCase(p.trim());
        }
    }
}