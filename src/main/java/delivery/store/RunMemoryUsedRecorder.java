package delivery.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Increments {@code memoryUsedCount} on disk when heal consumes run-memory hints. */
public final class RunMemoryUsedRecorder {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIR = "run-memory";

    private RunMemoryUsedRecorder() {
    }

    public static void recordUse(Path projectRoot, List<String> memIds) {
        if (projectRoot == null || memIds == null || memIds.isEmpty()) {
            return;
        }
        for (String id : memIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            bump(projectRoot, id.trim());
        }
    }

    @SuppressWarnings("unchecked")
    private static void bump(Path projectRoot, String memId) {
        Path file = projectRoot.resolve(DIR).resolve(memId + ".json");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, Object> doc = MAPPER.readValue(file.toFile(), Map.class);
            int count = toInt(doc.get("memoryUsedCount")) + 1;
            doc.put("memoryUsedCount", count);
            doc.put("updatedAt", Instant.now().toString());
            Path tmp = Path.of(file + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), doc);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
