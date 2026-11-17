package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pins approved run-memory into execute job folder for runner/heal (A4). */
@Service
public class RunMemoryJobBootstrap {
    /** Auto-retire a hint when it has never been used AND is older than this. */
    static final Duration STALE_AFTER = Duration.ofDays(90);

    private final PortalStore store;
    private final RunMemoryDiskStore disk = new RunMemoryDiskStore(null);
    private final ObjectMapper mapper = new ObjectMapper();

    public RunMemoryJobBootstrap(PortalStore store) {
        this.store = store;
    }

    public void attachApprovedSnapshot(String projectId, String jobId) {
        try {
            Path root = store.projectDiskRoot(projectId);
            List<Map<String, Object>> approved = disk.list(root, "approved");
            if (approved.isEmpty()) {
                return;
            }
            List<Map<String, Object>> live = autoRetireStale(root, approved);
            if (live.isEmpty()) {
                return;
            }
            Path jobDir = root.resolve("execute-runs").resolve(jobId.trim());
            Files.createDirectories(jobDir);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("jobId", jobId);
            doc.put("projectId", projectId);
            doc.put("items", live);
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(jobDir.resolve("run-memory-approved.json").toFile(), doc);
        } catch (Exception ignored) {
        }
    }

    /** Side-effect: retires stale entries on disk; returns the live set only. */
    private List<Map<String, Object>> autoRetireStale(Path root, List<Map<String, Object>> approved) {
        Instant now = Instant.now();
        List<Map<String, Object>> live = new ArrayList<>(approved.size());
        for (Map<String, Object> row : approved) {
            if (isStale(row, now)) {
                try {
                    disk.setStatus(root, String.valueOf(row.get("id")), "retired");
                } catch (Exception ignored) {
                    // best-effort — on failure, still omit from the snapshot
                }
                continue;
            }
            live.add(row);
        }
        return live;
    }

    private static boolean isStale(Map<String, Object> row, Instant now) {
        int used = asInt(row.get("memoryUsedCount"));
        if (used > 0) {
            return false;
        }
        Instant created = asInstant(row.get("createdAt"));
        return created != null && Duration.between(created, now).compareTo(STALE_AFTER) > 0;
    }

    private static int asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static Instant asInstant(Object o) {
        if (o == null) return null;
        try { return Instant.parse(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
