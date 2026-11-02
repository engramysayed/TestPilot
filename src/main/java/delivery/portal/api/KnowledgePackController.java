package delivery.portal.api;

import delivery.knowledge.KnowledgePack;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.KnowledgePackDiskStore;
import delivery.portal.service.KnowledgePackService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/knowledge-packs")
public class KnowledgePackController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final KnowledgePackService packs;

    public KnowledgePackController(
            PortalStore store,
            CurrentUserService currentUser,
            KnowledgePackService packs
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.packs = packs;
    }

    public record LibraryIngestRequest(List<String> tcIds, String title, Integer batchSize) {
    }

    public record UpdatePackRequest(String title, Integer batchSize, String markdown) {
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("projectId") String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (KnowledgePack pack : packs.list(projectId)) {
                items.add(toJson(pack, false));
            }
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{packId}")
    public ResponseEntity<?> get(
            @PathVariable("projectId") String projectId,
            @PathVariable("packId") String packId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return packs.get(projectId, packId)
                    .<ResponseEntity<?>>map(p -> ResponseEntity.ok(toJson(p, true)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Unknown knowledge pack").asMap()));
        } catch (KnowledgePackDiskStore.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(value = "/ingest/library", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ingestLibrary(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) LibraryIngestRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            List<String> tcIds = body == null ? List.of() : body.tcIds();
            String title = body == null ? null : body.title();
            int batch = body == null || body.batchSize() == null ? 30 : body.batchSize();
            KnowledgePack created = packs.ingestLibrary(projectId, tcIds, title, batch);
            return ResponseEntity.status(HttpStatus.CREATED).body(toJson(created, true));
        } catch (KnowledgePackService.BadIngestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(value = "/ingest/upload-tc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ingestUploadTc(
            @PathVariable("projectId") String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "batchSize", required = false) Integer batchSize
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            int batch = batchSize == null ? 30 : batchSize;
            KnowledgePack created = packs.ingestUploadTc(projectId, file, title, batch);
            return ResponseEntity.status(HttpStatus.CREATED).body(toJson(created, true));
        } catch (KnowledgePackService.BadIngestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(value = "/ingest/upload-md", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ingestUploadMd(
            @PathVariable("projectId") String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "batchSize", required = false) Integer batchSize
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            int batch = batchSize == null ? 30 : batchSize;
            KnowledgePack created = packs.ingestUploadMd(projectId, file, title, batch);
            return ResponseEntity.status(HttpStatus.CREATED).body(toJson(created, true));
        } catch (KnowledgePackService.BadIngestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PutMapping(value = "/{packId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putUpdate(
            @PathVariable("projectId") String projectId,
            @PathVariable("packId") String packId,
            @RequestBody(required = false) UpdatePackRequest body
    ) {
        return update(projectId, packId, body);
    }

    @PatchMapping(value = "/{packId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patchUpdate(
            @PathVariable("projectId") String projectId,
            @PathVariable("packId") String packId,
            @RequestBody(required = false) UpdatePackRequest body
    ) {
        return update(projectId, packId, body);
    }

    private ResponseEntity<?> update(String projectId, String packId, UpdatePackRequest body) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            String title = body == null ? null : body.title();
            Integer batch = body == null ? null : body.batchSize();
            String markdown = body == null ? null : body.markdown();
            KnowledgePack updated = packs.update(projectId, packId, title, batch, markdown);
            return ResponseEntity.ok(toJson(updated, true));
        } catch (KnowledgePackDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (KnowledgePackDiskStore.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    /** Serialize using the AE KnowledgePack field names (same JSON contract). */
    static Map<String, Object> toJson(KnowledgePack pack, boolean includeMarkdown) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("packId", pack.packId());
        m.put("projectId", pack.projectId());
        m.put("title", pack.title());
        m.put("source", pack.source().name());
        m.put("batchSize", pack.batchSize());
        m.put("revision", pack.revision());
        m.put("markdown", includeMarkdown ? pack.markdown() : "");
        m.put("markdownPath", pack.markdownPath());
        m.put("createdAt", pack.createdAt());
        m.put("updatedAt", pack.updatedAt());
        return m;
    }

    private static ResponseEntity<?> internalError(Exception e) {
        String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", msg).asMap());
    }
}
