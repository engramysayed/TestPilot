package delivery.agent.harness.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentPolicy;
import delivery.agent.harness.AgentTask;
import delivery.heal.ApiKeySource;
import delivery.portal.AgentHarnessProperties;
import delivery.privacy.ProviderPolicy;
import org.json.JSONArray;
import org.json.JSONObject;

import delivery.agent.harness.AgentStoreLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class CursorSidecarClient {
    private final AgentHarnessProperties properties;
    private final ObjectMapper mapper;

    public CursorSidecarClient(AgentHarnessProperties properties) {
        this(properties, new ObjectMapper());
    }

    public CursorSidecarClient(AgentHarnessProperties properties, ObjectMapper mapper) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public CursorSidecarSession execute(
            AgentTask task,
            Path taskDir,
            Path repoRoot,
            String mode,
            BooleanSupplier cancel) throws Exception {
        assertRuntimeReady();
        JSONObject req = buildRequest(task, taskDir, mode);
        List<String> cmd = parseCommand(properties.getCursorCommand());
        Map<String, String> env = new HashMap<>();
        env.put("CURSOR_API_KEY", ApiKeySource.resolve(ApiKeySource.CURSOR_API_KEY));
        env.putAll(delivery.runner.EngineProxy.fromSystem().nodeEnv());
        Duration deadline = task.budget() == null ? Duration.ofMinutes(properties.getMaxRuntimeMinutes()) : task.budget().maxRuntime();
        long lockMs = Long.getLong("delivery.agent-harness.store-lock-ms", 30_000L);
        AgentStoreLock storeLock = AgentStoreLock.acquire(taskDir, Duration.ofMillis(Math.max(100L, lockMs)));
        return new CursorSidecarSession(cmd, req.toString(), repoRoot, deadline, cancel, env, mapper, storeLock);
    }

    private void assertRuntimeReady() {
        if (!ProviderPolicy.fromEnvironment().allows(ProviderPolicy.Kind.CURSOR)) {
            throw new delivery.agent.harness.AgentHarnessException("PROVIDER_DENIED");
        }
        String key = ApiKeySource.resolve(ApiKeySource.CURSOR_API_KEY);
        if (key == null || key.isBlank()) {
            throw new delivery.agent.harness.AgentHarnessException("MISSING_API_KEY");
        }
    }

    JSONObject buildRequest(AgentTask task, Path taskDir, String op) {
        AgentToolPolicyMapper.MappedTools tools = AgentToolPolicyMapper.map(task.budget());
        JSONObject req = new JSONObject();
        req.put("protocolVersion", 1);
        req.put("op", op);
        req.put("taskId", task.taskId());
        req.put("parentJobId", task.parentJobId());
        req.put("objective", task.objective());
        req.put("workspacePath", task.workspacePath());
        req.put("taskDir", taskDir.toAbsolutePath().normalize().toString());
        req.put("storePath", taskDir.resolve("cursor-sdk-state").toString());
        req.put("tools", new JSONArray(tools.tools()));
        req.put("disallowedTools", new JSONArray(tools.disallowedTools()));
        req.put("agentName", "keel-harness:" + task.taskId());
        req.put("idempotencyKey", task.parentJobId() + ":" + task.taskId() + ":a" + task.attempt());
        if (task.agentId() != null && !task.agentId().isBlank()) {
            req.put("agentId", task.agentId());
        }
        if (task.agentRunId() != null && !task.agentRunId().isBlank()) {
            req.put("runId", task.agentRunId());
        }
        Duration maxRuntime = task.budget() == null
                ? Duration.ofMinutes(properties.getMaxRuntimeMinutes())
                : task.budget().maxRuntime();
        req.put("reconcileMaxPollMs", Math.min(120_000L, Math.max(15_000L, maxRuntime.toMillis())));
        return req;
    }

    static List<String> parseCommand(String command) {
        String cmd = command == null || command.isBlank()
                ? "node tools/cursor-agent/agent.mjs"
                : command.trim();
        List<String> parts = new ArrayList<>();
        for (String p : cmd.split("\\s+")) {
            if (!p.isBlank()) {
                parts.add(p);
            }
        }
        return parts;
    }

    public static Path resolveRepoRoot(String command) {
        Path userDir = Path.of(System.getProperty("user.dir", "."));
        if (command != null && command.contains("tools/cursor-agent")) {
            Path probe = userDir.resolve("tools/cursor-agent/agent.mjs");
            if (Files.isRegularFile(probe)) {
                return userDir;
            }
            Path walk = userDir;
            for (int i = 0; i < 6 && walk != null; i++) {
                if (Files.isRegularFile(walk.resolve("tools/cursor-agent/agent.mjs"))) {
                    return walk;
                }
                walk = walk.getParent();
            }
        }
        return userDir;
    }
}
