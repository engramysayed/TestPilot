package delivery.agent.harness.cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentStoreLock;
import delivery.agent.harness.AgentTaskStatus;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * One sidecar process: writes stdin request, reads stdout NDJSON until {@code result} or {@code error}.
 */
public final class CursorSidecarSession implements AutoCloseable {
    private final Process process;
    private final Thread reader;
    private final List<SidecarNdjsonLine> lines = new ArrayList<>();
    private final ObjectMapper mapper;
    private volatile AgentRunResult terminalResult;
    private volatile AgentHarnessException terminalError;
    private volatile String agentId;
    private volatile String runId;
    private final AgentStoreLock storeLock;

    public CursorSidecarSession(
            List<String> command,
            String stdinJson,
            Path repoRoot,
            Duration deadline,
            BooleanSupplier cancel,
            Map<String, String> extraEnv,
            ObjectMapper mapper,
            AgentStoreLock storeLock) throws Exception {
        this.storeLock = storeLock;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
        ProcessBuilder pb = new ProcessBuilder(command);
        if (repoRoot != null) {
            pb.directory(repoRoot.toFile());
        }
        pb.redirectErrorStream(false);
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
        }
        process = pb.start();
        try (OutputStreamWriter w = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            w.write(stdinJson);
            w.flush();
        }
        process.getOutputStream().close();

        AtomicBoolean done = new AtomicBoolean();
        reader = new Thread(() -> drainStdout(done), "cursor-sidecar-stdout");
        reader.setDaemon(true);
        reader.start();

        Duration limit = deadline == null ? Duration.ofMinutes(15) : deadline;
        long deadlineNs = System.nanoTime() + limit.toNanos();
        while (process.isAlive() && !done.get()) {
            if (cancel != null && cancel.getAsBoolean()) {
                process.destroyForcibly();
                if (terminalError == null) {
                    terminalError = new AgentHarnessException("CANCEL_REQUESTED");
                }
                break;
            }
            if (System.nanoTime() >= deadlineNs) {
                process.destroyForcibly();
                if (terminalError == null) {
                    terminalError = new AgentHarnessException("SIDECAR_TIMEOUT");
                }
                break;
            }
            process.waitFor(50, TimeUnit.MILLISECONDS);
        }
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        reader.join(2000);
        if (terminalResult == null && terminalError == null && process.exitValue() != 0) {
            terminalError = new AgentHarnessException("SIDECAR_DIED exit=" + process.exitValue());
        }
    }

    private void drainStdout(AtomicBoolean done) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isBlank() || !line.startsWith("{")) {
                    continue;
                }
                SidecarNdjsonLine parsed;
                try {
                    parsed = SidecarNdjsonLine.parse(line, mapper);
                } catch (Exception skip) {
                    continue;
                }
                synchronized (lines) {
                    lines.add(parsed);
                }
                handle(parsed);
                if ("result".equals(parsed.type()) || "error".equals(parsed.type())) {
                    done.set(true);
                }
            }
        } catch (Exception e) {
            terminalError = new AgentHarnessException("sidecar stdout read failed: " + e.getMessage(), e);
            done.set(true);
        }
    }

  @SuppressWarnings("unchecked")
    private void handle(SidecarNdjsonLine line) {
        switch (line.type()) {
            case "run.started" -> {
                agentId = line.text("agentId");
                runId = line.text("runId");
            }
            case "result" -> {
                String st = line.text("status");
                AgentTaskStatus status;
                try {
                    status = AgentTaskStatus.valueOf(st);
                } catch (Exception e) {
                    status = AgentTaskStatus.COMPLETED;
                }
                List<String> modified = line.raw().has("modifiedFiles")
                        ? mapper.convertValue(line.raw().get("modifiedFiles"), List.class)
                        : List.of();
                terminalResult = new AgentRunResult(
                        status, line.text("summary"), line.payloadMap(), modified, "result.json");
            }
            case "error" -> terminalError = new AgentHarnessException(
                    line.text("code") + ": " + line.text("message"));
            default -> { }
        }
    }

    public String agentId() {
        return agentId;
    }

    public String runId() {
        return runId;
    }

    public boolean isTerminal() {
        return terminalResult != null || terminalError != null;
    }

    public AgentRunResult resultOrThrow() {
        if (terminalError != null) {
            throw terminalError;
        }
        if (terminalResult == null) {
            throw new AgentHarnessException("sidecar ended without result");
        }
        return terminalResult;
    }

    @Override
    public void close() throws java.io.IOException {
        try {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        } finally {
            if (storeLock != null) {
                storeLock.close();
            }
        }
    }
}
