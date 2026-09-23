package delivery.portal.service;

import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkspacePackageService {
    private final ProjectRepository projects;
    private final PortalStore store;
    private final ProjectArtifactService artifacts;

    public WorkspacePackageService(ProjectRepository projects, PortalStore store,
                                   ProjectArtifactService artifacts) {
        this.projects = projects;
        this.store = store;
        this.artifacts = artifacts;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPackagesForOwner(Long ownerUserId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProjectEntity project : projects.findByOwnerUserIdOrderByIdDesc(ownerUserId)) {
            if (project.isArchived()) {
                continue;
            }
            try {
                Path root = store.projectDiskRoot(project.getProjectId());
                Map<String, Object> tree = artifacts.buildTree(root);
                List<Map<String, Object>> packages = (List<Map<String, Object>>) tree.get("packages");
                if (packages == null) {
                    continue;
                }
                for (Map<String, Object> pkg : packages) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String projectId = project.getProjectId();
                    row.put("projectId", projectId);
                    row.put("projectName", project.getName());
                    row.put("path", pkg.get("path"));
                    row.put("label", pkg.get("label"));
                    row.put("sizeBytes", pkg.get("sizeBytes"));
                    row.put("workspaceRole", store.workspaceRole(projectId, ownerUserId));
                    row.put("canOperate", store.canOperate(projectId, ownerUserId));
                    row.put("jobRunning", store.hasRunningJob(projectId));
                    rows.add(row);
                }
            } catch (Exception ignored) {
                // Skip projects with unreadable artifact trees.
            }
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> r) -> String.valueOf(r.get("projectName")))
                .thenComparing(r -> String.valueOf(r.get("label")), Comparator.reverseOrder()));
        return rows;
    }
}
