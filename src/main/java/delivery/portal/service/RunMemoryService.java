package delivery.portal.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RunMemoryService {
    private final PortalStore store;
    private final RunMemoryDiskStore disk = new RunMemoryDiskStore(null);

    public RunMemoryService(PortalStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> list(String projectId, String status) throws IOException {
        return disk.list(store.projectDiskRoot(projectId), status);
    }

    public Optional<Map<String, Object>> get(String projectId, String memId) throws IOException {
        return disk.get(store.projectDiskRoot(projectId), memId);
    }

    public Map<String, Object> propose(String projectId, String memId, String summary, String locatorHint, String sourceJobId)
            throws IOException {
        return disk.propose(store.projectDiskRoot(projectId), memId, summary, locatorHint, sourceJobId);
    }

    public Map<String, Object> approve(String projectId, String memId) throws IOException {
        return disk.setStatus(store.projectDiskRoot(projectId), memId, "approved");
    }

    public Map<String, Object> retire(String projectId, String memId) throws IOException {
        return disk.setStatus(store.projectDiskRoot(projectId), memId, "retired");
    }
}
