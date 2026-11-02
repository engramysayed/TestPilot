package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.GenerateFromKnowledgeRunStore;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/generate-from-knowledge")
public class GenerateFromKnowledgeController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final GenerateFromKnowledgeRunStore runs = new GenerateFromKnowledgeRunStore();

    public GenerateFromKnowledgeController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public record CreateRunRequest(List<String> packIds, String title, Integer batchSize) {
    }

    @PostMapping(value = "/runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createRun(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) CreateRunRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            List<String> packIds = body == null ? List.of() : body.packIds();
            String title = body == null ? null : body.title();
            Integer batch = body == null ? null : body.batchSize();
            Map<String, Object> rec = runs.create(
                    store.projectDiskRoot(projectId), projectId, packIds, title, batch);
            return ResponseEntity.status(HttpStatus.CREATED).body(rec);
        } catch (GenerateFromKnowledgeRunStore.BadRequestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", msg).asMap());
        }
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRun(
            @PathVariable("projectId") String projectId,
            @PathVariable("runId") String runId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return runs.load(store.projectDiskRoot(projectId), runId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Unknown generate-from-knowledge run").asMap()));
        } catch (GenerateFromKnowledgeRunStore.BadRequestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", msg).asMap());
        }
    }
}
