package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.RunMemoryDiskStore;
import delivery.portal.service.RunMemoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/run-memory")
public class RunMemoryController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final RunMemoryService memory;

    public RunMemoryController(PortalStore store, CurrentUserService currentUser, RunMemoryService memory) {
        this.store = store;
        this.currentUser = currentUser;
        this.memory = memory;
    }

    public record ProposeBody(String id, String summary, String locatorHint, String sourceJobId) {
    }

    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String status
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(memory.list(projectId, status));
        } catch (Exception e) {
            return internal(e);
        }
    }

    @GetMapping("/{memId}")
    public ResponseEntity<?> get(@PathVariable String projectId, @PathVariable String memId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return memory.get(projectId, memId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Memory not found").asMap()));
        } catch (Exception e) {
            return internal(e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> propose(@PathVariable String projectId, @RequestBody(required = false) ProposeBody body) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (body == null || body.id() == null || body.id().isBlank()) {
            return bad("id is required");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(memory.propose(
                    projectId, body.id(), body.summary(), body.locatorHint(), body.sourceJobId()));
        } catch (RunMemoryDiskStore.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", e.getMessage()).asMap());
        } catch (RunMemoryDiskStore.BadRequestException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return internal(e);
        }
    }

    @PostMapping(path = "/{memId}/approve")
    public ResponseEntity<?> approve(@PathVariable String projectId, @PathVariable String memId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(memory.approve(projectId, memId));
        } catch (RunMemoryDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (RunMemoryDiskStore.BadRequestException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return internal(e);
        }
    }

    @PostMapping(path = "/{memId}/retire")
    public ResponseEntity<?> retire(@PathVariable String projectId, @PathVariable String memId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(memory.retire(projectId, memId));
        } catch (RunMemoryDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (RunMemoryDiskStore.BadRequestException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return internal(e);
        }
    }

    private static ResponseEntity<?> bad(String msg) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", msg).asMap());
    }

    private static ResponseEntity<?> internal(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", e.getMessage() == null ? "error" : e.getMessage()).asMap());
    }
}
