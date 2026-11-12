package delivery.agent.harness.cursor;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.heal.ApiKeySource;
import delivery.portal.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Opt-in live validation through Java coordinator + real {@code agent.mjs}.
 * Run: {@code call api-keys.local.bat && mvn test -pl keel-portal -Dkeel.agent.cursor.live=true -Dtest=CursorAgentRuntimeLiveIT}
 */
public class CursorAgentRuntimeLiveIT {

    @BeforeClass
    public void assumeLive() {
        if (!Boolean.getBoolean("keel.agent.cursor.live")) {
            throw new org.testng.SkipException("Set -Dkeel.agent.cursor.live=true");
        }
        if (!ApiKeySource.isPresent(ApiKeySource.CURSOR_API_KEY)) {
            throw new org.testng.SkipException("CURSOR_API_KEY not available (call api-keys.local.bat before mvn)");
        }
    }

    @Test
    public void coordinatorExecute_realCursorSidecar() throws Exception {
        Path repoRoot = CursorSidecarClient.resolveRepoRoot("node tools/cursor-agent/agent.mjs");
        Path agentScript = repoRoot.resolve("tools/cursor-agent/agent.mjs");
        if (!Files.isRegularFile(agentScript)) {
            throw new org.testng.SkipException("agent.mjs not found at " + agentScript);
        }

        Path project = Files.createTempDirectory("live-coordinator-");
        Path sourceWs = Files.createTempDirectory("live-source-ws-");
        Files.writeString(sourceWs.resolve("readme.txt"), "live java path\n");

        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + agentScript.toAbsolutePath().normalize());
        props.setMaxRuntimeMinutes(5);

        String prevAllow = System.getProperty("delivery.provider.allowlist");
        System.setProperty("delivery.provider.allowlist", "cursor");
        try {
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        var task = coordinator.createTask(project, new NewAgentTaskRequest(
                "job_live_java",
                "p1",
                delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                "Read readme.txt and reply with exactly: {\"ok\":true}",
                List.of(),
                sourceWs.toString()));

        AgentRunResult result = coordinator.execute(project, task.parentJobId(), task.taskId());
        Assert.assertEquals(result.terminalStatus(), AgentTaskStatus.COMPLETED);
        Assert.assertTrue(result.summary() != null && !result.summary().isBlank());

        Path taskDir = new delivery.agent.harness.AgentRunStore()
                .taskDir(project, task.parentJobId(), task.taskId());
        Assert.assertTrue(Files.isRegularFile(taskDir.resolve("sdk-touch.marker")));
        Assert.assertTrue(Files.isDirectory(taskDir.resolve("workspace")));
        } finally {
            if (prevAllow == null) {
                System.clearProperty("delivery.provider.allowlist");
            } else {
                System.setProperty("delivery.provider.allowlist", prevAllow);
            }
        }
    }
}
