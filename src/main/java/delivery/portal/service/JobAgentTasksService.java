package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentRunStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Summarises agent-harness tasks for a single parent job so the UI can render
 * a "AI validation" chip (queued / running / ok / fail) next to the run's status.
 *
 * <p>Status values the overall label maps to (see {@link delivery.agent.harness.AgentTaskStatus}):
 * {@code PENDING}/{@code INTERRUPTED} → queued, {@code RUNNING} → running,
 * {@code COMPLETED} → ok, {@code FAILED}/{@code TIMED_OUT}/{@code CANCELLED} → failed.
 */
@Service
public class JobAgentTasksService {
    private static final ObjectMapper JSON = new ObjectMapper();
    /** Global "running agents" count per project root is re-scanned at most this often. */
    private static final long COUNT_CACHE_TTL_MS = 2_000L;

    private final PortalStore store;
    private final ConcurrentHashMap<Path, RunningCount> runningCache = new ConcurrentHashMap<>();

    private record RunningCount(long expiresAt, int runningTotal) {
    }

    public JobAgentTasksService(PortalStore store) {
        this.store = store;
    }

    public Map<String, Object> summarise(String projectId, String jobId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("present", false);
        out.put("overall", "none");
        out.put("tasks", List.of());
        if (projectId == null || jobId == null || projectId.isBlank() || jobId.isBlank()) {
            return out;
        }
        Path projectRoot;
        try {
            projectRoot = store.filesystemStoreFor(projectId).projectRoot(projectId);
        } catch (RuntimeException e) {
            return out;
        }
        Path jobDir = projectRoot.resolve(AgentRunStore.ROOT_DIR).resolve(jobId);
        if (!Files.isDirectory(jobDir)) {
            return out;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Stream<Path> taskDirs = Files.list(jobDir)) {
            taskDirs
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(taskDir -> {
                        Map<String, Object> row = readTask(taskDir);
                        if (row != null) rows.add(row);
                    });
        } catch (IOException e) {
            return out;
        }
        if (rows.isEmpty()) {
            return out;
        }
        out.put("present", true);
        out.put("overall", pickOverall(rows));
        out.put("queuedAhead", countAhead(projectRoot, jobDir));
        out.put("tasks", rows);
        return out;
    }

    private Map<String, Object> readTask(Path taskDir) {
        Path file = taskDir.resolve("task.json");
        if (!Files.isRegularFile(file)) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = JSON.readValue(file.toFile(), Map.class);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", String.valueOf(raw.getOrDefault("taskId", taskDir.getFileName().toString())));
            row.put("taskType", String.valueOf(raw.getOrDefault("taskType", "")));
            row.put("status", String.valueOf(raw.getOrDefault("taskStatus", "PENDING")));
            Object createdAt = raw.get("createdAt");
            if (createdAt != null) row.put("createdAt", String.valueOf(createdAt));
            return row;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * How many tasks on this project are RUNNING, minus whatever this job contributes.
     * The scan is cached per project root for {@link #COUNT_CACHE_TTL_MS} so a page of N rows
     * polling every 5 s doesn't re-walk the whole agent-runs tree on every row.
     */
    private int countAhead(Path projectRoot, Path thisJobDir) {
        Path agentRuns = projectRoot.resolve(AgentRunStore.ROOT_DIR);
        if (!Files.isDirectory(agentRuns)) return 0;
        long now = System.currentTimeMillis();
        RunningCount cached = runningCache.get(projectRoot);
        int total = (cached != null && cached.expiresAt() > now)
                ? cached.runningTotal()
                : refreshRunning(projectRoot, agentRuns, now);
        int thisJob = countRunningIn(thisJobDir);
        return Math.max(0, total - thisJob);
    }

    private int refreshRunning(Path projectRoot, Path agentRuns, long now) {
        int running = 0;
        try (Stream<Path> jobs = Files.list(agentRuns)) {
            for (Path jobDir : (Iterable<Path>) jobs::iterator) {
                if (!Files.isDirectory(jobDir)) continue;
                running += countRunningIn(jobDir);
            }
        } catch (IOException ignored) {
            // best-effort
        }
        runningCache.put(projectRoot, new RunningCount(now + COUNT_CACHE_TTL_MS, running));
        return running;
    }

    private int countRunningIn(Path jobDir) {
        if (jobDir == null || !Files.isDirectory(jobDir)) return 0;
        int running = 0;
        try (Stream<Path> tasks = Files.list(jobDir)) {
            for (Path t : (Iterable<Path>) tasks::iterator) {
                if (!Files.isDirectory(t)) continue;
                Map<String, Object> row = readTask(t);
                if (row != null && "RUNNING".equals(row.get("status"))) running++;
            }
        } catch (IOException ignored) {
            // best-effort
        }
        return running;
    }

    private String pickOverall(List<Map<String, Object>> rows) {
        boolean anyRunning = false, anyPending = false, anyFailed = false, allCompleted = true;
        for (Map<String, Object> r : rows) {
            String s = String.valueOf(r.get("status"));
            if (!"COMPLETED".equals(s)) allCompleted = false;
            if ("RUNNING".equals(s)) anyRunning = true;
            else if ("PENDING".equals(s) || "INTERRUPTED".equals(s)) anyPending = true;
            else if ("FAILED".equals(s) || "TIMED_OUT".equals(s) || "CANCELLED".equals(s)) anyFailed = true;
        }
        if (anyRunning) return "running";
        if (anyPending) return "queued";
        if (allCompleted && !anyFailed) return "ok";
        if (anyFailed) return "failed";
        return "mixed";
    }
}
