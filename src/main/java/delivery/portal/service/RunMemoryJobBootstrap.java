package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pins approved run-memory into execute job folder for runner/heal (A4). */
@Service
public class RunMemoryJobBootstrap {
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
            Path jobDir = root.resolve("execute-runs").resolve(jobId.trim());
            Files.createDirectories(jobDir);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("jobId", jobId);
            doc.put("projectId", projectId);
            doc.put("items", approved);
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(jobDir.resolve("run-memory-approved.json").toFile(), doc);
        } catch (Exception ignored) {
        }
    }
}
