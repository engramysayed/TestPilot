package delivery.portal.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StructuredPlanService {
    private final PortalStore store;
    private final StructuredPlanDiskStore disk = new StructuredPlanDiskStore(null);

    public StructuredPlanService(PortalStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> list(String projectId) throws IOException {
        return disk.list(store.projectDiskRoot(projectId));
    }

    public Optional<Map<String, Object>> get(String projectId, String planId) throws IOException {
        return disk.get(store.projectDiskRoot(projectId), planId);
    }

    public Map<String, Object> create(String projectId, String planId, String name, List<Map<String, Object>> steps)
            throws IOException {
        return disk.create(store.projectDiskRoot(projectId), planId, name, steps);
    }

    public Map<String, Object> update(String projectId, String planId, String name,
                                      List<Map<String, Object>> steps, Integer expectedRevision) throws IOException {
        return disk.update(store.projectDiskRoot(projectId), planId, name, steps, expectedRevision);
    }
}
