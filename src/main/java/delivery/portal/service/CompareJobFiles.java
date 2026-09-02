package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Payload and result files for background model-compare jobs. */
public final class CompareJobFiles {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Payload(String stories, String modelA, String modelB) {
    }

    private CompareJobFiles() {
    }

    public static Path payloadPath(String projectId, String jobId) {
        return Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId, "compare", jobId + ".json");
    }

    public static Path resultPath(String storeRoot, String projectId, String jobId) {
        return Path.of(storeRoot, projectId, "compare-runs", jobId, "result.json");
    }

    public static void writePayload(Path file, Payload payload) throws Exception {
        Files.createDirectories(file.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
    }

    public static Payload readPayload(Path file) throws Exception {
        return MAPPER.readValue(file.toFile(), Payload.class);
    }

    public static void writeResult(Path file, Object result) throws Exception {
        Files.createDirectories(file.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), result);
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> readResult(Path file) throws Exception {
        return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), java.util.Map.class);
    }
}
