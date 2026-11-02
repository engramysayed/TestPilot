package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Heal proposal store + Accept/Reject. Accept patches existing generated Java only.
 */
@Service
public class HealProposalService {
    private final PortalStore store;
    private final HealProposalDiskStore disk;

    public HealProposalService(PortalStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.disk = new HealProposalDiskStore(objectMapper);
    }

    public Path projectRoot(String projectId) {
        return store.projectDiskRoot(projectId);
    }

    public List<Map<String, Object>> list(String projectId, String status) throws IOException {
        return disk.list(projectRoot(projectId), status);
    }

    public Optional<Map<String, Object>> get(String projectId, String proposalId) throws IOException {
        return disk.load(projectRoot(projectId), proposalId);
    }

    public Map<String, Object> create(String projectId, Map<String, Object> body) throws IOException {
        Map<String, Object> input = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        input.putIfAbsent("projectId", projectId);
        if (!projectId.equals(String.valueOf(input.get("projectId")))) {
            input.put("projectId", projectId);
        }
        return disk.create(projectRoot(projectId), input);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> accept(String projectId, String proposalId) throws IOException {
        Map<String, Object> proposal = disk.load(projectRoot(projectId), proposalId)
                .orElseThrow(() -> new HealProposalDiskStore.NotFoundException(
                        "Heal proposal not found: " + proposalId));
        String status = String.valueOf(proposal.getOrDefault("status", ""));
        if (!HealProposalDiskStore.STATUS_PENDING.equals(status)) {
            throw new HealProposalDiskStore.ConflictException("Heal proposal already decided: " + status);
        }
        Map<String, Object> before = (Map<String, Object>) proposal.get("locatorBefore");
        Map<String, Object> after = (Map<String, Object>) proposal.get("locatorAfter");
        String pageClass = String.valueOf(proposal.get("pageClass"));
        HealJavaLocatorPatcher.PatchOutcome patch = HealJavaLocatorPatcher.applyLocatorPatch(
                projectRoot(projectId), pageClass, before, after);
        Map<String, Object> updated = disk.markStatus(
                projectRoot(projectId), proposalId, HealProposalDiskStore.STATUS_ACCEPTED);
        Map<String, Object> out = new LinkedHashMap<>(updated);
        out.put("patchedFile", patch.pageFile().getFileName().toString());
        out.put("patchDetail", patch.detail());
        return out;
    }

    public Map<String, Object> reject(String projectId, String proposalId) throws IOException {
        return disk.markStatus(projectRoot(projectId), proposalId, HealProposalDiskStore.STATUS_REJECTED);
    }
}
