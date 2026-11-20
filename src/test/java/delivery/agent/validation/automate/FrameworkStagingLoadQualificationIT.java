package delivery.agent.validation.automate;

import delivery.agent.harness.cursor.CursorSidecarClient;
import delivery.agent.validation.contract.FrameworkContractValidator;
import delivery.agent.validation.contract.ValidationReport;
import delivery.heal.ApiKeySource;
import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Phase 6.15 staging load qualification — **staging only**.
 *
 * <p>Skipped unless {@code -Dkeel.agent.staging.load.qualification=true},
 * {@code -Dkeel.agent.staging.load.approved=true}, {@code -Dkeel.agent.staging.load.host=…}, and
 * {@code CURSOR_API_KEY} are set. Do not run on a developer workstation without staging approval.
 */
public class FrameworkStagingLoadQualificationIT {

    private static final Path TEMPLATE_ROOT = Path.of("customer-framework-template").toAbsolutePath().normalize();

    private static final List<FrameworkRepairQualificationFixtures.Scenario> SERIAL_SCENARIOS = List.of(
            FrameworkRepairQualificationFixtures.Scenario.MISSING_TEST,
            FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_LOCATORS,
            FrameworkRepairQualificationFixtures.Scenario.EMPTY_TEST_STUB);

    private static final List<FrameworkRepairQualificationFixtures.Scenario> CONCURRENT_SCENARIOS = List.of(
            FrameworkRepairQualificationFixtures.Scenario.MISSING_PAGE_ACTIONS,
            FrameworkRepairQualificationFixtures.Scenario.MIXED_INVENTORY_BAD_IMPORT);

    private final Map<String, Path> workByParentJob = new ConcurrentHashMap<>();

    private String stagingHost;
    private Path repoRoot;

    @BeforeClass
    public void requireStagingApproval() {
        if (!Boolean.getBoolean("keel.agent.staging.load.qualification")) {
            throw new SkipException("Set -Dkeel.agent.staging.load.qualification=true (staging only)");
        }
        if (!Boolean.getBoolean("keel.agent.staging.load.approved")) {
            throw new SkipException("Set -Dkeel.agent.staging.load.approved=true after ops approval");
        }
        stagingHost = System.getProperty("keel.agent.staging.load.host");
        if (stagingHost == null || stagingHost.isBlank()) {
            throw new SkipException("Set -Dkeel.agent.staging.load.host=<staging-hostname>");
        }
        if (!ApiKeySource.isPresent(ApiKeySource.CURSOR_API_KEY)) {
            throw new SkipException("CURSOR_API_KEY not available");
        }
        if (!Boolean.getBoolean("keel.agent.cursor.compile.on")) {
            throw new SkipException("Set -Dkeel.agent.cursor.compile.on=true");
        }
        if (!compileCheckEnabled()) {
            throw new SkipException("delivery.emit-compile-check must be true");
        }
        repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        if (!Files.isRegularFile(repoRoot.resolve("tools/cursor-agent/agent.mjs"))) {
            throw new SkipException("agent.mjs missing");
        }
    }

    @Test
    public void baseline_capture() throws Exception {
        FrameworkStagingLoadQualificationEvidence.writeBaseline(repoRoot, stagingHost);
        Assert.assertTrue(
                Files.isRegularFile(repoRoot.resolve("docs/handoff/phase6-15-staging-baseline.json")),
                "baseline evidence");
    }

    @Test(dependsOnMethods = "baseline_capture")
    public void serialSoak_threeCycles() throws Exception {
        for (int i = 0; i < SERIAL_SCENARIOS.size(); i++) {
            var scenario = SERIAL_SCENARIOS.get(i);
            String parentJob = "staging-serial-" + (i + 1);
            AutomateFrameworkAcceptanceOutcome outcome = runScenario(scenario, parentJob);
            Path work = workByParentJob.get(parentJob);
            boolean compilePassed = compilePassed(work, outcome);
            FrameworkStagingLoadQualificationEvidence.writeCycle(
                    repoRoot, "serial", i + 1, scenario.name(), parentJob, work, outcome, compilePassed);
            Thread.sleep(2_000L);
        }
    }

    @Test(dependsOnMethods = "serialSoak_threeCycles")
    public void concurrent_twoRepairs() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_SCENARIOS.size(); i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    var scenario = CONCURRENT_SCENARIOS.get(idx);
                    String parentJob = idx == 0 ? "staging-conc-a" : "staging-conc-b";
                    AutomateFrameworkAcceptanceOutcome outcome = runScenario(scenario, parentJob);
                    Path work = workByParentJob.get(parentJob);
                    boolean compilePassed = compilePassed(work, outcome);
                    Map<String, Object> job = FrameworkStagingLoadQualificationEvidence.cycleMetrics(work, parentJob, outcome);
                    job.put("scenario", scenario.name());
                    job.put("parentJob", parentJob);
                    job.put("compilePassed", compilePassed);
                    job.put("workDir", work.toAbsolutePath().normalize().toString());
                    return job;
                }));
            }
            List<Map<String, Object>> jobs = new ArrayList<>();
            for (Future<Map<String, Object>> f : futures) {
                jobs.add(f.get());
            }
            Assert.assertEquals(jobs.size(), 2);
            String id0 = stringOrEmpty(jobs.get(0).get("taskId"));
            String id1 = stringOrEmpty(jobs.get(1).get("taskId"));
            Assert.assertNotEquals(id0, id1, "distinct task ids");
            String a0 = stringOrEmpty(jobs.get(0).get("agentId"));
            String a1 = stringOrEmpty(jobs.get(1).get("agentId"));
            if (!a0.isBlank() && !a1.isBlank()) {
                Assert.assertNotEquals(a0, a1, "distinct agent ids when runs started");
            }
            FrameworkStagingLoadQualificationEvidence.writeConcurrentPair(repoRoot, jobs);
        }
    }

    private AutomateFrameworkAcceptanceOutcome runScenario(
            FrameworkRepairQualificationFixtures.Scenario scenario, String parentJob) throws Exception {
        FrameworkRepairQualificationFixtures.Prepared prepared = FrameworkRepairQualificationFixtures.prepare(scenario);
        Path work = prepared.workDir();
        Path project = prepared.project();
        workByParentJob.put(parentJob, work);
        var inventory = prepared.inventory();
        ValidationReport preRepair = FrameworkContractValidator.validateWorkspace(project, TEMPLATE_ROOT, inventory);
        Assert.assertFalse(preRepair.passed());

        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "cursor");
        try {
            var gate = AutomateFrameworkAcceptanceGate.forConversionJob(
                    stagingHarnessProps(repoRoot), () -> false, Duration.ofMinutes(15));
            return gate.run(work, project, TEMPLATE_ROOT, work, parentJob, "proj-staging", inventory);
        } finally {
            if (prevAllow == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prevAllow);
            }
        }
    }

    private static boolean compilePassed(Path work, AutomateFrameworkAcceptanceOutcome outcome) {
        Path compileOk = work.resolve("framework-acceptance/emit-compile-check.ok");
        return outcome.status() != AutomateFrameworkAcceptanceStatus.COMPILE_FAILED && Files.isRegularFile(compileOk);
    }

    private static AgentHarnessProperties stagingHarnessProps(Path repoRoot) {
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setAutomateFrameworkRepairEnabled(true);
        props.setMaxRuntimeMinutes(12);
        props.setMaxConcurrentRuns(2);
        props.setCursorCommand("node " + repoRoot.resolve("tools/cursor-agent/agent.mjs").toAbsolutePath());
        return props;
    }

    private static boolean compileCheckEnabled() {
        String p = System.getProperty("delivery.emit-compile-check");
        if (p == null || p.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(p.trim());
    }

    private static String stringOrEmpty(Object o) {
        return o == null ? "" : o.toString();
    }
}