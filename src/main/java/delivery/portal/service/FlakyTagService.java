package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class FlakyTagService {
    private final PortalStore store;
    private final FlakyTagDiskStore disk;

    public FlakyTagService(PortalStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.disk = new FlakyTagDiskStore(objectMapper);
    }

    public Path projectRoot(String projectId) {
        return store.projectDiskRoot(projectId);
    }

    public List<Map<String, Object>> list(String projectId, boolean includeCleared) throws IOException {
        return disk.list(projectRoot(projectId), includeCleared);
    }

    public Map<String, Object> add(
            String projectId,
            String tcId,
            Integer stepIndex,
            String reason,
            String sourceJobId,
            String signal
    ) throws IOException {
        return disk.add(projectRoot(projectId), tcId, stepIndex, reason, sourceJobId, signal);
    }

    public Map<String, Object> clear(String projectId, String tagId) throws IOException {
        return disk.clear(projectRoot(projectId), tagId);
    }
}
