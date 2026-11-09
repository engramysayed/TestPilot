package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** A6: summarize vision assertion evidence under an execute job for API + failure pack. */
@Service
public class JobVisionSummaryService {

    private static final String VISUAL_ASSERT_JSON = "visual-assert.json";

    private final PortalStore store;
    private final ObjectMapper mapper;

    public JobVisionSummaryService(PortalStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    public Map<String, Object> summarize(String projectId, String jobId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("usedVision", false);
        out.put("attemptCount", 0);
        out.put("successfulFallbackCount", 0);
        out.put("domFirst", delivery.vision.VisionGroundingConfig.domPostClickEnabled());
        out.put("assertProvider", delivery.vision.VisionGroundingConfig.assertProviderId());
        try {
            Path evidence = store.projectDiskRoot(projectId).resolve("execute-runs").resolve(jobId.trim())
                    .resolve("evidence");
            if (!Files.isDirectory(evidence)) {
                return out;
            }
            List<Map<String, Object>> attempts = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(evidence, 6)) {
                walk.filter(p -> VISUAL_ASSERT_JSON.equals(p.getFileName().toString()))
                        .filter(Files::isRegularFile)
                        .forEach(p -> readAssert(p).ifPresent(attempts::add));
            }
            int count = attempts.size();
            long success = attempts.stream()
                    .filter(a -> "PASSED".equalsIgnoreCase(String.valueOf(a.get("status"))
                            ) || "SUCCESS".equalsIgnoreCase(String.valueOf(a.get("status"))))
                    .count();
            long fallback = attempts.stream()
                    .filter(a -> {
                        String st = String.valueOf(a.get("status")).toUpperCase(Locale.ROOT);
                        return "FAILED".equals(st) || "UNCERTAIN".equals(st);
                    })
                    .count();
            out.put("usedVision", count > 0);
            out.put("attemptCount", count);
            out.put("successfulFallbackCount", (int) success);
            out.put("fallbackOrUncertainCount", (int) fallback);
            out.put("attempts", List.copyOf(attempts));
        } catch (IOException ignored) {
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private java.util.Optional<Map<String, Object>> readAssert(Path file) {
        try {
            Map<String, Object> raw = mapper.readValue(file.toFile(), Map.class);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tcId", raw.getOrDefault("tcId", ""));
            row.put("status", raw.getOrDefault("status", ""));
            row.put("provider", raw.getOrDefault("provider", ""));
            row.put("model", raw.getOrDefault("model", ""));
            row.put("confidence", raw.getOrDefault("confidence", 0));
            return java.util.Optional.of(row);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
}
