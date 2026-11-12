package delivery.agent.harness.cursor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentWorkspacePreparer;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.heal.ApiKeySource;
import delivery.portal.AgentHarnessProperties;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Live: kill execute sidecar after {@code run.started}, recover in a fresh JVM via coordinator reconcile
 * ({@code Agent.getRun}+{@code wait}, not {@code Agent.create} / {@code listRuns}).
 *
 * Run: {@code call api-keys.local.bat && mvn test -pl keel-portal -Dkeel.agent.cursor.live=true -Dtest=CursorActiveRunRecoveryLiveIT}
 */
public class CursorActiveRunRecoveryLiveIT {

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
    public void recoverAfterSidecarKill_freshJvmCoordinator() throws Exception {
        Path repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        Path agentScript = repoRoot.resolve("tools/cursor-agent/agent.mjs");
        if (!Files.isRegularFile(agentScript)) {
            throw new org.testng.SkipException("agent.mjs missing");
        }

        Path project = Files.createTempDirectory("live-recover-project-");
        Path sourceWs = Files.createTempDirectory("live-recover-ws-");
        Files.writeString(sourceWs.resolve("readme.txt"), "recover live test\n");

        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + agentScript.toAbsolutePath().normalize());
        props.setMaxRuntimeMinutes(10);

        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "cursor");
        try {
            var coordinator = delivery.agent.harness.AgentHarnessCoordinator.forTests(props);
            var created = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_live_recover",
                    "p1",
                    delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                    "Read readme.txt carefully and write a detailed multi-paragraph analysis before answering.",
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

            CursorSidecarClient client = new CursorSidecarClient(props);
            JSONObject req = client.buildRequest(task, taskDir, "execute");
            List<String> cmd = CursorSidecarClient.parseCommand(props.getCursorCommand());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoRoot.toFile());
            pb.environment().put("CURSOR_API_KEY", ApiKeySource.resolve(ApiKeySource.CURSOR_API_KEY));
            Process sidecar = pb.start();

            try (OutputStreamWriter w = new OutputStreamWriter(sidecar.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(req.toString());
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
            Assert.assertNotNull(agentId, "run.started agentId");
            Assert.assertNotNull(runId, "run.started runId");

            sidecar.destroyForcibly();
            Assert.assertTrue(sidecar.waitFor(15, TimeUnit.SECONDS));

            agentsBefore = Math.max(agentsBefore, countNdjsonAgents(taskDir.resolve("cursor-sdk-state/agents.ndjson")));

            AgentTask running = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
            running = running
                    .withStatus(AgentTaskStatus.RUNNING)
                    .withRun(new delivery.agent.harness.AgentRunHandle(agentId, runId, java.time.Instant.now()))
                    .withPolicy(isoPolicy)
                    .withWorkspacePath(isoPath);
            store.saveTask(project, running);
            store.saveLaunchState(project, created.parentJobId(), created.taskId(), new delivery.agent.harness.AgentLaunchState(
                    "RUNNING", agentId, runId, null, null,
                    created.parentJobId() + ":" + created.taskId() + ":a1", 1));

            Path evidence = project.resolve("recovery-child.json");
            String cp = testClasspath();
            ProcessBuilder recoveryPb = new ProcessBuilder(
                    javaBin(),
                    "-cp", cp,
                    CursorActiveRunRecoveryRunner.class.getName(),
                    project.toAbsolutePath().toString(),
                    created.parentJobId(),
                    created.taskId(),
                    agentScript.toAbsolutePath().toString(),
                    evidence.toAbsolutePath().toString());
            recoveryPb.redirectErrorStream(true);
            recoveryPb.environment().put("CURSOR_API_KEY", ApiKeySource.resolve(ApiKeySource.CURSOR_API_KEY));
            Path childLog = project.resolve("recovery-child.log");
            recoveryPb.redirectOutput(childLog.toFile());
            Process recovery = recoveryPb.start();
            int exit = recovery.waitFor();
            Assert.assertEquals(exit, 0, childLogMessage(childLog));
            Assert.assertTrue(Files.isRegularFile(evidence));

            JsonNode child = new ObjectMapper().readTree(Files.readString(evidence));
            String terminal = child.path("terminalStatus").asText();
            Assert.assertTrue(
                    AgentTaskStatus.COMPLETED.name().equals(terminal)
                            || AgentTaskStatus.CANCELLED.name().equals(terminal),
                    "terminal=" + terminal);

            int agentsAfter = countNdjsonAgents(taskDir.resolve("cursor-sdk-state/agents.ndjson"));
            Assert.assertEquals(agentsAfter, Math.max(1, agentsBefore), "unexpected extra agent row in store");

            Assert.assertTrue(Files.isRegularFile(taskDir.resolve("result.json")));
            AgentTask done = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
            Assert.assertTrue(
                    done.taskStatus() == AgentTaskStatus.COMPLETED
                            || done.taskStatus() == AgentTaskStatus.CANCELLED);
            Assert.assertEquals(done.agentId(), agentId);
            Assert.assertEquals(done.agentRunId(), runId);
        } finally {
            if (prevAllow == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prevAllow);
            }
        }
    }

    private static String childLogMessage(Path log) {
        try {
            return Files.isRegularFile(log) ? Files.readString(log) : "no child log";
        } catch (Exception e) {
            return "log read failed";
        }
    }

    private static int countNdjsonAgents(Path agentsFile) throws Exception {
        if (!Files.isRegularFile(agentsFile)) {
            return 0;
        }
        return (int) Files.readAllLines(agentsFile).stream().filter(l -> !l.isBlank()).count();
    }

    private static String testClasspath() {
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.isBlank()) {
            cp = System.getProperty("java.class.path");
        }
        return cp;
    }

    private static String javaBin() {
        String home = System.getProperty("java.home");
        boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        return Path.of(home, "bin", win ? "java.exe" : "java").toString();
    }
}
