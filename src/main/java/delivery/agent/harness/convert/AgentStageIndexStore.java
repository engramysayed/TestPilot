package delivery.agent.harness.convert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.identity.PublicationLock;
import delivery.store.Atomic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** {@code agent-runs/{parentJobId}/stage-index.json} with separate lock file. */
public final class AgentStageIndexStore {
    public static final String STAGE_CONVERT_PRE_PUBLISH = "CONVERT_PRE_PUBLISH";

    private final ObjectMapper mapper = new ObjectMapper();

    public record TaskRef(String inputFingerprint, String taskId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StageEntry {
        public TaskRef active;
        public TaskRef deferred;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndexFile {
        public Map<String, StageEntry> stages = new java.util.LinkedHashMap<>();
    }

    public Path indexFile(Path projectRoot, String parentJobId) {
        return projectRoot.resolve(AgentRunStore.ROOT_DIR).resolve(parentJobId).resolve("stage-index.json");
    }

    public Path lockFile(Path projectRoot, String parentJobId) {
        return projectRoot.resolve(AgentRunStore.ROOT_DIR).resolve(parentJobId).resolve(".stage-index.lock");
    }

    public IndexFile read(Path projectRoot, String parentJobId) throws IOException {
        Path file = indexFile(projectRoot, parentJobId);
        if (!Files.isRegularFile(file)) {
            return new IndexFile();
        }
        return mapper.readValue(file.toFile(), IndexFile.class);
    }

    public void write(Path projectRoot, String parentJobId, IndexFile index) throws IOException {
        Path file = indexFile(projectRoot, parentJobId);
        Files.createDirectories(file.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(index);
        Atomic.writeString(file, json, StandardCharsets.UTF_8);
    }

    public <T> T withLock(Path projectRoot, String parentJobId, PublicationLock.Body<T> body) throws Exception {
        return PublicationLock.call(lockFile(projectRoot, parentJobId), body);
    }

    public boolean isTerminal(AgentTaskStatus status) {
        return status == AgentTaskStatus.COMPLETED
                || status == AgentTaskStatus.FAILED
                || status == AgentTaskStatus.CANCELLED
                || status == AgentTaskStatus.TIMED_OUT;
    }

    public Optional<TaskRef> activeFor(Path projectRoot, String parentJobId, AgentRunStore store) throws IOException {
        StageEntry entry = read(projectRoot, parentJobId).stages.get(STAGE_CONVERT_PRE_PUBLISH);
        if (entry == null || entry.active == null) {
            return Optional.empty();
        }
        return Optional.of(entry.active);
    }

    public Optional<AgentTask> loadActiveTask(Path projectRoot, String parentJobId, AgentRunStore store)
            throws IOException {
        Optional<TaskRef> active = activeFor(projectRoot, parentJobId, store);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        return store.loadTask(projectRoot, parentJobId, active.get().taskId());
    }
}
