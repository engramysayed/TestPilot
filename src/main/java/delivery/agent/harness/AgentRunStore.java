package delivery.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import delivery.store.Atomic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Files under {@code {projectRoot}/agent-runs/{parentJobId}/{taskId}/}.
 */
public final class AgentRunStore {
    public static final String ROOT_DIR = "agent-runs";
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");

    private final ObjectMapper mapper;

    public AgentRunStore() {
        this(new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    public AgentRunStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public Path taskDir(Path projectRoot, String parentJobId, String taskId) {
        validateId(parentJobId, "parentJobId");
        validateId(taskId, "taskId");
        return projectRoot.resolve(ROOT_DIR).resolve(parentJobId).resolve(taskId);
    }

    public void saveTask(Path projectRoot, AgentTask task) throws IOException {
        Path dir = taskDir(projectRoot, task.parentJobId(), task.taskId());
        Files.createDirectories(dir);
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(task);
        Atomic.writeString(dir.resolve("task.json"), json, StandardCharsets.UTF_8);
    }

    public Optional<AgentTask> loadTask(Path projectRoot, String parentJobId, String taskId) throws IOException {
        Path file = taskDir(projectRoot, parentJobId, taskId).resolve("task.json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(mapper.readValue(file.toFile(), AgentTask.class));
    }

    public void saveContextManifest(Path projectRoot, String parentJobId, String taskId, AgentContextManifest manifest)
            throws IOException {
        Path dir = taskDir(projectRoot, parentJobId, taskId);
        Files.createDirectories(dir);
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        Atomic.writeString(dir.resolve("context-manifest.json"), json, StandardCharsets.UTF_8);
    }

    public void appendEvent(Path projectRoot, String parentJobId, String taskId, AgentEvent event) throws IOException {
        Path file = taskDir(projectRoot, parentJobId, taskId).resolve("events.jsonl");
        Files.createDirectories(file.getParent());
        String line = mapper.writeValueAsString(event) + System.lineSeparator();
        Files.writeString(file, line, StandardCharsets.UTF_8,
                Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
    }

    public void saveResult(Path projectRoot, String parentJobId, String taskId, AgentRunResult result) throws IOException {
        Path dir = taskDir(projectRoot, parentJobId, taskId);
        Files.createDirectories(dir);
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        Atomic.writeString(dir.resolve("result.json"), json, StandardCharsets.UTF_8);
    }

    public Optional<AgentRunResult> loadResult(Path projectRoot, String parentJobId, String taskId) throws IOException {
        Path file = taskDir(projectRoot, parentJobId, taskId).resolve("result.json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(mapper.readValue(file.toFile(), AgentRunResult.class));
    }

    private static void validateId(String id, String label) {
        if (id == null || id.isBlank() || !SAFE_ID.matcher(id.trim()).matches()) {
            throw new IllegalArgumentException(label + " must match " + SAFE_ID.pattern());
        }
    }
}
