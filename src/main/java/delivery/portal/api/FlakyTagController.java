package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.FlakyTagDiskStore;
import delivery.portal.service.FlakyTagService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Flaky tag store/API. DELETE soft-clears (sets clearedAt).
 */
@RestController
@RequestMapping("/api/projects/{projectId}/flaky-tags")
public class FlakyTagController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final FlakyTagService flakyTags;

    public FlakyTagController(PortalStore store, CurrentUserService currentUser, FlakyTagService flakyTags) {
        this.store = store;
        this.currentUser = currentUser;
        this.flakyTags = flakyTags;
    }

    public record CreateRequest(
            String tcId,
            Integer stepIndex,
            String reason,
            String sourceJobId,
            String signal
    ) {
    }

    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "includeCleared", required = false, defaultValue = "false") boolean includeCleared
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(flakyTags.list(projectId, includeCleared));
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) CreateRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            String tcId = body == null ? null : body.tcId();
            Integer stepIndex = body == null ? null : body.stepIndex();
            String reason = body == null ? null : body.reason();
            String sourceJobId = body == null ? null : body.sourceJobId();
            String signal = body == null ? null : body.signal();
            Map<String, Object> created = flakyTags.add(projectId, tcId, stepIndex, reason, sourceJobId, signal);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (FlakyTagDiskStore.BadRequestException | FlakyTagDiskStore.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<?> clear(
            @PathVariable("projectId") String projectId,
            @PathVariable("tagId") String tagId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(flakyTags.clear(projectId, tagId));
        } catch (FlakyTagDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (FlakyTagDiskStore.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    private static ResponseEntity<?> internalError(Exception e) {
        String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", msg).asMap());
    }
}
