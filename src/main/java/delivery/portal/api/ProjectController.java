package delivery.portal.api;

import delivery.portal.model.CreateCredentialRequest;
import delivery.portal.model.PatchCredentialRequest;
import delivery.portal.model.PatchProjectRequest;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectArtifactService;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.service.ProjectTcService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final ProjectTcService projectTcs;
    private final ProjectCredentialService credentials;
    private final ProjectArtifactService artifacts;

    public ProjectController(PortalStore store, CurrentUserService currentUser,
                             ProjectTcService projectTcs, ProjectCredentialService credentials,
                             ProjectArtifactService artifacts) {
        this.store = store;
        this.currentUser = currentUser;
        this.projectTcs = projectTcs;
        this.credentials = credentials;
        this.artifacts = artifacts;
    }

    public record CreateProjectRequest(String name, String baseUrl) {
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        Long uid = currentUser.requireUserId();
        return store.listProjects(uid, includeArchived).stream()
                .map(this::projectToMap)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody(required = false) CreateProjectRequest body) {
        String name = body == null ? null : body.name();
        String baseUrl = body == null ? null : body.baseUrl();
        ProjectRecord project = store.createProject(name, baseUrl, currentUser.requireUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "projectId", project.getProjectId(),
                "name", project.getName()
        ));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<?> get(@PathVariable("projectId") String projectId) {
        Long uid = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, uid).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        store.ensureBaseUrlBackfill(projectId);
        return store.getOwnedProject(projectId, uid)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(projectToMap(p)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown project").asMap()));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<?> patch(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) PatchProjectRequest body) {
        PatchProjectRequest patch = body == null ? new PatchProjectRequest(null, null, null) : body;
        if (patch.archived() != null && patch.archived() && store.hasRunningJob(projectId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("JOB_RUNNING", "Wait for job to finish").asMap());
        }
        return store.updateOwnedProject(projectId, currentUser.requireUserId(), patch)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(projectToMap(p)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown project").asMap()));
    }

    private Map<String, Object> projectToMap(ProjectRecord p) {
        Map<String, Object> map = new HashMap<>();
        map.put("projectId", p.getProjectId());
        map.put("name", p.getName());
        map.put("hasStoredFramework", store.hasStoredFramework(p.getProjectId()));
        map.put("latestVersion", p.getLatestVersion());
        map.put("baseUrl", p.getBaseUrl());
        map.put("archived", p.isArchived());
        map.put("lastModified", p.getLastModified());
        map.put("lastModifiedLabel", p.getLastModifiedLabel());
        return map;
    }

    @GetMapping("/{projectId}/credentials")
    public ResponseEntity<?> listCredentials(@PathVariable("projectId") String projectId) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        return ResponseEntity.ok(credentials.list(projectId).stream()
                .map(this::credentialToMap)
                .collect(Collectors.toList()));
    }

    @PostMapping("/{projectId}/credentials")
    public ResponseEntity<?> createCredential(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) CreateCredentialRequest body) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "Request body required").asMap());
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(credentialToMap(credentials.create(
                            projectId, body.profileName(), body.username(), body.password())));
        } catch (ProjectCredentialService.DuplicateProfileException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("DUPLICATE_PROFILE", e.getMessage()).asMap());
        }
    }

    @PatchMapping("/{projectId}/credentials/{profileName}")
    public ResponseEntity<?> patchCredential(
            @PathVariable("projectId") String projectId,
            @PathVariable("profileName") String profileName,
            @RequestBody(required = false) PatchCredentialRequest body) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        PatchCredentialRequest patch = body == null ? new PatchCredentialRequest(null, null) : body;
        return credentials.update(projectId, profileName, patch.username(), patch.password())
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(credentialToMap(c)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown credential profile").asMap()));
    }

    @DeleteMapping("/{projectId}/credentials/{profileName}")
    public ResponseEntity<?> deleteCredential(
            @PathVariable("projectId") String projectId,
            @PathVariable("profileName") String profileName) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (!credentials.delete(projectId, profileName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown credential profile").asMap());
        }
        return ResponseEntity.ok(Map.of(
                "projectId", projectId,
                "profileName", profileName,
                "deleted", true
        ));
    }

    private Map<String, Object> credentialToMap(ProjectCredentialService.CredentialSummary c) {
        Map<String, Object> map = new HashMap<>();
        map.put("profileName", c.profileName());
        map.put("username", c.username());
        map.put("hasPassword", c.hasPassword());
        return map;
    }

    @GetMapping("/{projectId}/artifacts")
    public ResponseEntity<?> listArtifacts(@PathVariable("projectId") String projectId) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            Path root = store.projectDiskRoot(projectId);
            return ResponseEntity.ok(artifacts.buildTree(root));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", e.getMessage() == null ? "Failed to list artifacts" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/{projectId}/artifacts/preview")
    public ResponseEntity<?> previewArtifact(
            @PathVariable("projectId") String projectId,
            @RequestParam("path") String path) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            String content = artifacts.preview(store.projectDiskRoot(projectId), path);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(content);
        } catch (ProjectArtifactService.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_PATH", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", e.getMessage() == null ? "Preview failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/{projectId}/artifacts/download")
    public ResponseEntity<?> downloadArtifact(
            @PathVariable("projectId") String projectId,
            @RequestParam("path") String path) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            Path file = artifacts.downloadZip(store.projectDiskRoot(projectId), path);
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getFileName().toString() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(file))
                    .body(resource);
        } catch (ProjectArtifactService.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_PATH", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", e.getMessage() == null ? "Download failed" : e.getMessage()).asMap());
        }
    }

    @DeleteMapping("/{projectId}/artifacts")
    public ResponseEntity<?> deleteArtifact(
            @PathVariable("projectId") String projectId,
            @RequestParam("path") String path) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (store.hasRunningJob(projectId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("JOB_RUNNING", "Wait for job to finish").asMap());
        }
        try {
            artifacts.delete(store.projectDiskRoot(projectId), path);
            return ResponseEntity.ok(Map.of(
                    "projectId", projectId,
                    "path", artifacts.validateRelativePath(path),
                    "deleted", true
            ));
        } catch (ProjectArtifactService.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_PATH", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", e.getMessage() == null ? "Delete failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/{projectId}/tcs")
    public ResponseEntity<?> listTcs(@PathVariable("projectId") String projectId) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            return ResponseEntity.ok(Map.of(
                    "projectId", projectId,
                    "lastRunAt", projectTcs.conversionRunLabel(projectId),
                    "tcs", projectTcs.listTcs(projectId)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("IR_READ_FAILED", e.getMessage() == null ? "IR read failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/{projectId}/tcs/{tcId}")
    public ResponseEntity<?> getTc(
            @PathVariable("projectId") String projectId,
            @PathVariable("tcId") String tcId
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            return ResponseEntity.ok(projectTcs.getTc(projectId, tcId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("IR_READ_FAILED", e.getMessage() == null ? "IR read failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/{projectId}/tcs/{tcId}/screenshots/{fileName}")
    public ResponseEntity<?> screenshot(
            @PathVariable("projectId") String projectId,
            @PathVariable("tcId") String tcId,
            @PathVariable("fileName") String fileName
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        Path file = projectTcs.resolveScreenshot(projectId, tcId, fileName).orElse(null);
        if (file == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Screenshot not found").asMap());
        }
        Resource resource = new FileSystemResource(file);
        MediaType type = fileName.toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(type)
                .body(resource);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<?> delete(@PathVariable("projectId") String projectId) {
        boolean deleted = store.deleteOwnedProject(projectId, currentUser.requireUserId());
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        return ResponseEntity.ok(Map.of(
                "projectId", projectId,
                "deleted", true
        ));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteAll() {
        int n = store.deleteAllOwnedProjects(currentUser.requireUserId());
        return ResponseEntity.ok(Map.of(
                "deleted", true,
                "count", n
        ));
    }
}
