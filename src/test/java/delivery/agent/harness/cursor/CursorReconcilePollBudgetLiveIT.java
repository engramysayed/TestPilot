package delivery.agent.harness.cursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentWorkspacePreparer;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.heal.ApiKeySource;
import delivery.agent.harness.AgentHarnessProperties;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Live Scenario B (Policy A): reconcile poll budget exhausted → {@code INTERRUPTED} NDJSON;
 * no {@code cancelRun} / cancel sidecar op during reconcile.
 *
 * Run: {@code call api-keys.local.bat && mvn test -pl keel-portal -Dkeel.agent.cursor.live=true -Dtest=CursorReconcilePollBudgetLiveIT}
 */
public class CursorReconcilePollBudgetLiveIT {

    @BeforeClass
    public void assumeLive() {
        if (!Boolean.getBoolean("keel.agent.cursor.live")) {
            throw new org.testng.SkipException("Set -Dkeel.agent.cursor.live=true");
        }
        if (!ApiKeySource.isPresent(ApiKeySource.CURSOR_API_KEY)) {
            throw new org.testng.SkipException("CURSOR_API_KEY not available");
        }
    }

    @Test
    public void reconcilePollBudget_exhausted_emitsInterrupted_noCancelSidecar() throws Exception {
        Path repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        Path agentScript = repoRoot.resolve("tools/cursor-agent/agent.mjs");
        if (!Files.isRegularFile(agentScript)) {
            throw new org.testng.SkipException("agent.mjs missing");
        }

        Path project = Files.createTempDirectory("live-poll-budget-");
        Path sourceWs = Files.createTempDirectory("live-poll-ws-");
        Files.writeString(sourceWs.resolve("readme.txt"), "poll budget live test\n");

        AgentHarnessProperties executeProps = new AgentHarnessProperties();
        executeProps.setEnabled(true);
        executeProps.setCursorEnabled(true);
        executeProps.setCursorCommand("node " + agentScript.toAbsolutePath().normalize());
        executeProps.setMaxRuntimeMinutes(10);

        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "cursor");
        try {
            var coordinator = delivery.agent.harness.AgentHarnessCoordinator.forTests(executeProps);
            var created = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_live_poll",
                    "p1",
                    delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                    "Read readme.txt and produce a long, slow, multi-paragraph analysis. Do not finish for several minutes.",
                    List.of(),
                    sourceWs.toString()));

            AgentRunStore store = new AgentRunStore();
            Path taskDir = store.taskDir(project, created.parentJobId(), created.taskId());
            Path iso = new AgentWorkspacePreparer().ensureIsolatedCopy(taskDir, sourceWs);
            AgentTask task = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
            String isoPath = iso.toAbsolutePath().normalize().toString();
            var isoPolicy = new delivery.agent.harness.AgentPolicy(
                    task.budget().maxRuntime(),
                    task.budget().maxAttempts(),
                    task.budget().allowedTools(),
                    List.of(isoPath),
                    task.budget().networkAllowed(),
                    task.budget().requireHumanApproval());
            task = task.withWorkspacePath(isoPath).withPolicy(isoPolicy);
            store.saveTask(project, task);

            int agentsBefore = countNdjsonAgents(taskDir.resolve("cursor-sdk-state/agents.ndjson"));

            CursorSidecarClient client = new CursorSidecarClient(executeProps);
            JSONObject executeReq = client.buildRequest(task, taskDir, "execute");
            List<String> cmd = CursorSidecarClient.parseCommand(executeProps.getCursorCommand());
            ProcessBuilder executePb = new ProcessBuilder(cmd);
            executePb.directory(repoRoot.toFile());
            executePb.environment().put("CURSOR_API_KEY", ApiKeySource.resolve(ApiKeySource.CURSOR_API_KEY));
            Process sidecar = executePb.start();

            try (OutputStreamWriter w = new OutputStreamWriter(sidecar.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(executeReq.toString());
                w.flush();
            }
            sidecar.getOutputStream().close();

            String agentId = null;
            String runId = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(sidecar.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
                while ((line = reader.readLine()) != null && System.nanoTime() < deadline) {
                    line = line.trim();
                    if (!line.startsWith("{")) {
                        continue;
                    }
                    JsonNode node = new ObjectMapper().readTree(line);
                    if ("run.started".equals(node.path("type").asText())) {
                        agentId = node.path("agentId").asText(null);
                        runId = node.path("runId").asText(null);
                        break;
                    }
                }
            }
            Assert.assertNotNull(agentId, "execute run.started agentId");
            Assert.assertNotNull(runId, "execute run.started runId");

            sidecar.destroyForcibly();
            Assert.assertTrue(sidecar.waitFor(15, TimeUnit.SECONDS));

            int agentsAfterExecute = countNdjsonAgents(taskDir.resolve("cursor-sdk-state/agents.ndjson"));
            Assert.assertEquals(agentsAfterExecute, Math.max(1, agentsBefore), "extra agent after execute");

            AgentTask running = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
            running = running
                    .withStatus(AgentTaskStatus.RUNNING)
                    .withRun(new delivery.agent.harness.AgentRunHandle(agentId, runId, Instant.now()))
                    .withPolicy(isoPolicy)
                    .withWorkspacePath(isoPath);
            var shortPollPolicy = new delivery.agent.harness.AgentPolicy(
                    java.time.Duration.ofSeconds(15),
                    isoPolicy.maxAttempts(),
                    isoPolicy.allowedTools(),
                    isoPolicy.allowedWorkspaceRoots(),
                    isoPolicy.networkAllowed(),
                    isoPolicy.requireHumanApproval());
            running = running.withPolicy(shortPollPolicy);
            store.saveTask(project, running);

            AgentHarnessProperties reconcileProps = new AgentHarnessProperties();
            reconcileProps.setCursorCommand(executeProps.getCursorCommand());
            CursorSidecarClient reconcileClient = new CursorSidecarClient(reconcileProps);
            JSONObject reconcileReq = reconcileClient.buildRequest(running, taskDir, "reconcile");
            long pollMs = reconcileReq.getLong("reconcileMaxPollMs");
            Assert.assertEquals(pollMs, 15_000L, "reconcileMaxPollMs should floor at 15s");

            ProcessBuilder reconcilePb = new ProcessBuilder(cmd);
            reconcilePb.directory(repoRoot.toFile());
            reconcilePb.environment().put("CURSOR_API_KEY", ApiKeySource.resolve(ApiKeySource.CURSOR_API_KEY));
            Process reconcileProc = reconcilePb.start();

            List<String> ndjsonRaw = new ArrayList<>();
            try (OutputStreamWriter w = new OutputStreamWriter(reconcileProc.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(reconcileReq.toString());
                w.flush();
            }
            reconcileProc.getOutputStream().close();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(reconcileProc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
                while ((line = reader.readLine()) != null && System.nanoTime() < deadline) {
                    ndjsonRaw.add(line);
                    JsonNode node = new ObjectMapper().readTree(line.trim());
                    if ("result".equals(node.path("type").asText())) {
                        break;
                    }
                }
            }
            reconcileProc.waitFor(30, TimeUnit.SECONDS);
            if (reconcileProc.isAlive()) {
                reconcileProc.destroyForcibly();
            }

            Files.write(project.resolve("reconcile-poll-ndjson.jsonl"), ndjsonRaw, StandardCharsets.UTF_8);

            List<JsonNode> events = CursorLiveSidecarEvidence.parseNdjsonLines(ndjsonRaw);
            Map<String, Object> evidence = new LinkedHashMap<>(CursorLiveSidecarEvidence.summarizeNdjson(events));
            evidence.put("agentIdAtExecute", agentId);
            evidence.put("runIdAtExecute", runId);
            evidence.put("reconcileMaxPollMs", pollMs);
            evidence.put("agentsNdjsonBefore", agentsBefore);
            evidence.put("agentsNdjsonAfterExecute", agentsAfterExecute);
            evidence.put("agentsNdjsonAfterReconcile",
                    countNdjsonAgents(taskDir.resolve("cursor-sdk-state/agents.ndjson")));
            evidence.put("op", "reconcile-only");
            evidence.put("cancelOpInvoked", false);
            CursorLiveSidecarEvidence.writeEvidence(project.resolve("reconcile-poll-evidence.json"), evidence);

            Assert.assertFalse(CursorLiveSidecarEvidence.ndjsonContainsCancelLifecycle(events),
                    "reconcile must not emit cancel lifecycle");
            String terminal = CursorLiveSidecarEvidence.resultStatus(events);
            Assert.assertEquals(terminal, "INTERRUPTED", "Policy A poll budget terminal");
            Assert.assertTrue(events.stream()
                            .anyMatch(n -> "sdk.event".equals(n.path("type").asText())
                                    && "reconcile.poll".equals(n.path("stream").asText())),
                    "expected reconcile.poll sdk events");
            Assert.assertEquals(evidence.get("agentsNdjsonAfterReconcile"), agentsAfterExecute,
                    "no duplicate agent row after reconcile");
        } finally {
            if (prevAllow == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prevAllow);
            }
        }
    }

    private static int countNdjsonAgents(Path agentsFile) throws Exception {
        if (!Files.isRegularFile(agentsFile)) {
            return 0;
        }
        return (int) Files.readAllLines(agentsFile).stream().filter(l -> !l.isBlank()).count();
    }
}
