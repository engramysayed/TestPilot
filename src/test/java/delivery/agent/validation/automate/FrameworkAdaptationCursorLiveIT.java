package delivery.agent.validation.automate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Opt-in live FRAMEWORK_ADAPTATION_V1 qualification (Phase 6.5).
 *
 * {@code call api-keys.local.bat && mvn -pl keel-portal test -Dkeel.agent.cursor.live.framework=true -Dtest=FrameworkAdaptationCursorLiveIT -Ddelivery.emit-compile-check=false}
 */
public class FrameworkAdaptationCursorLiveIT {

    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeClass
    public void assumeLive() {
        if (!Boolean.getBoolean("keel.agent.cursor.live.framework")) {
            throw new org.testng.SkipException("Set -Dkeel.agent.cursor.live.framework=true");
        }
        if (!ApiKeySource.isPresent(ApiKeySource.CURSOR_API_KEY)) {
            throw new org.testng.SkipException("CURSOR_API_KEY not available");
        }
        Path repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        if (!Files.isRegularFile(repoRoot.resolve("tools/cursor-agent/agent.mjs"))) {
            throw new org.testng.SkipException("agent.mjs missing");
        }
    }

    @Test
    public void missingExpectedTest_liveQualification() throws Exception {
        runQualification(FrameworkRepairQualificationFixtures.Scenario.MISSING_TEST);
    }

    @Test
    public void multiPageBadImport_liveQualification() throws Exception {
        runQualification(FrameworkRepairQualificationFixtures.Scenario.MULTI_PAGE_BAD_IMPORT);
    }

    @Test
    public void missingPageLocators_liveQualification() throws Exception {
        runQualification(FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_LOCATORS);
    }

    @Test
    public void missingPageActions_liveQualification() throws Exception {
        runQualification(FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_ACTIONS);
    }

    @Test
    public void missingPageAssertions_liveQualification() throws Exception {
        runQualification(FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_ASSERTIONS);
    }

    @Test
    public void emptyTestStub_liveQualification() throws Exception {
        runQualification(FrameworkRepairQualificationFixtures.Scenario.EMPTY_TEST_STUB);
    }

    @Test
    public void mixedInventory_badImport_liveQualification() throws Exception {
        runQualification(FrameworkRepairQualificationFixtures.Scenario.MIXED_INVENTORY_BAD_IMPORT);
    }

    @Test
    public void missingExpectedPage_liveRepairLifecycle() throws Exception {
        runQualificationLegacyMissingPage();
    }

    private void runQualification(FrameworkRepairQualificationFixtures.Scenario scenario) throws Exception {
        var prepared = FrameworkRepairQualificationFixtures.prepare(scenario);
        runScenario(prepared, scenario.name());
    }

    private void runQualificationLegacyMissingPage() throws Exception {
        var step = new delivery.codegen.ProvenStep("TC_1", "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        var emit = delivery.codegen.CodegenEmitGate.prepare(List.of(new delivery.job.TcOutcome(
                "TC_1", "login", delivery.job.TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        Path work = Files.createTempDirectory("live-fw-missing_page-");
        Path project = work.resolve("project");
        FrameworkRepairQualificationFixtures.seedCompilableProject(project);
        new delivery.codegen.CodeWriter(Path.of("customer-framework-template/templates")).write(project, emit.outcomes());
        Files.deleteIfExists(project.resolve("src/main/java/project/pages/Login_Page.java"));
        var inv = delivery.agent.validation.contract.FrameworkExpectedInventory.fromOutcomes(emit.outcomes());
        runScenario(new FrameworkRepairQualificationFixtures.Prepared(work, project, inv, "MISSING_PAGE"), "MISSING_PAGE");
    }

    private void runScenario(FrameworkRepairQualificationFixtures.Prepared prepared, String scenarioId) throws Exception {
        Path work = prepared.workDir();
        Path project = prepared.project();
        var inventory = prepared.inventory();

        ValidationReport preRepair = FrameworkContractValidator.validateWorkspace(project, TEMPLATE_ROOT, inventory);
        Assert.assertFalse(preRepair.passed(), "fixture must fail mandatory checks before repair");

        Path repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        String parentJob = "live-" + scenarioId;
        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "cursor");
        AutomateFrameworkAcceptanceOutcome outcome;
        try {
            var gate = AutomateFrameworkAcceptanceGate.forConversionJob(
                    liveHarnessProps(repoRoot), () -> false, Duration.ofMinutes(12));
            outcome = gate.run(work, project, TEMPLATE_ROOT, work, parentJob, "proj-live", inventory);
        } finally {
            if (prevAllow == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prevAllow);
            }
        }

        Path snapshot = work.resolve("framework-acceptance/original");
        Assert.assertTrue(Files.isDirectory(snapshot));
        Assert.assertFalse(
                FrameworkContractValidator.validateWorkspace(snapshot, TEMPLATE_ROOT, inventory).passed(),
                "snapshot preserves violated state");

        if (outcome.accepted()) {
            Assert.assertTrue(outcome.revalidationReport().passed());
            assertArchitectureQuality(project, inventory, scenarioId);
        }

        writeQualificationEvidence(work, scenarioId, parentJob, outcome, preRepair, inventory);
    }

    private static void assertArchitectureQuality(
            Path project,
            delivery.agent.validation.contract.FrameworkExpectedInventory inventory,
            String scenarioId) throws Exception {
        for (String pageClass : inventory.expectedPageClassNames()) {
            Path page = project.resolve("src/main/java/project/pages/" + pageClass + ".java");
            if (!Files.exists(page)) {
                continue;
            }
            String body = Files.readString(page);
            Assert.assertTrue(body.contains("package project.pages;"), scenarioId + " page package");
            Assert.assertTrue(body.contains("public class " + pageClass), scenarioId + " page class name");
        }
    }

    private void writeQualificationEvidence(
            Path work,
            String scenarioId,
            String parentJob,
            AutomateFrameworkAcceptanceOutcome outcome,
            ValidationReport preRepair,
            delivery.agent.validation.contract.FrameworkExpectedInventory inventory) throws Exception {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("scenario", scenarioId);
        evidence.put("accepted", outcome.accepted());
        evidence.put("gateStatus", outcome.status().name());
        evidence.put("detail", outcome.detail());
        evidence.put("initialMandatoryRuleIds", preRepair.findings().stream()
                .filter(f -> f.severity() == delivery.agent.validation.contract.ValidationSeverity.MANDATORY)
                .map(f -> f.ruleId())
                .distinct()
                .sorted()
                .toList());
        evidence.put("initialMandatoryCount", preRepair.mandatoryMessages().size());
        evidence.put("revalidationPassed", outcome.accepted() && outcome.revalidationReport().passed());

        AgentRunStore store = new AgentRunStore();
        String taskId = outcome.taskId();
        if (taskId == null || taskId.isBlank()) {
            Path tasksRoot = work.resolve("agent-runs").resolve(parentJob);
            if (Files.isDirectory(tasksRoot)) {
                try (var dirs = Files.list(tasksRoot)) {
                    taskId = dirs.filter(Files::isDirectory)
                            .map(p -> p.getFileName().toString())
                            .sorted()
                            .reduce((a, b) -> b)
                            .orElse("");
                }
            }
        }
        evidence.put("taskId", taskId);
        if (taskId != null && !taskId.isBlank()) {
            var taskOpt = store.loadTask(work, parentJob, taskId);
            if (taskOpt.isPresent()) {
                AgentTask task = taskOpt.get();
                evidence.put("agentId", task.agentId());
                evidence.put("agentRunId", task.agentRunId());
                evidence.put("provider", task.providerId().name());
                evidence.put("terminalTaskStatus", task.taskStatus().name());
            }
        }

        Path reconManifest = work.resolve("framework-acceptance/reconciliation-manifest.json");
        if (Files.isRegularFile(reconManifest)) {
            evidence.put("reconciliation", JSON.readTree(Files.readString(reconManifest)));
        } else if (outcome.status() == AutomateFrameworkAcceptanceStatus.RECONCILIATION_FAILED) {
            evidence.put("reconciliation", "failed");
        } else {
            evidence.put("reconciliation", "not_applicable_or_missing");
        }

        Path revalidateReport = work.resolve("agent-runs")
                .resolve(parentJob + "/acceptance-revalidate")
                .resolve("contract-validation-report.json");
        if (Files.isRegularFile(revalidateReport)) {
            JsonNode node = JSON.readTree(Files.readString(revalidateReport));
            evidence.put("revalidationPassedOnDisk", node.path("passed").asBoolean());
        }

        evidence.put("promoted", outcome.accepted());
        evidence.put("permittedSourceFiles", listPermittedFiles(work, parentJob, taskId));
        evidence.put("repairCandidateFiles", listPermittedFilesUnder(work.resolve("framework-acceptance/repair-candidate")));

        Path dest = Path.of("docs/handoff/phase6-5-live-evidence-" + scenarioId + ".json");
        Files.createDirectories(dest.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), evidence);
        JSON.writerWithDefaultPrettyPrinter().writeValue(work.resolve("phase6-5-evidence.json").toFile(), evidence);
    }

    private static List<String> listPermittedFiles(Path work, String parentJob, String taskId) throws Exception {
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }
        Path ws = work.resolve("agent-runs").resolve(parentJob).resolve(taskId).resolve("workspace");
        return listPermittedFilesUnder(ws);
    }

    private static List<String> listPermittedFilesUnder(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (FrameworkRepairWorkspaceReconciler.isPermittedRelative(rel)) {
                    paths.add(rel);
                }
            });
        }
        return paths.stream().sorted().collect(Collectors.toList());
    }

    private static AgentHarnessProperties liveHarnessProps(Path repoRoot) {
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setAutomateFrameworkRepairEnabled(true);
        props.setMaxRuntimeMinutes(10);
        props.setCursorCommand("node " + repoRoot.resolve("tools/cursor-agent/agent.mjs").toAbsolutePath());
        return props;
    }
}