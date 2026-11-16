package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.LocatorMemoryService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/locator-memory")
public class LocatorMemoryController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final LocatorMemoryService memory;

    public LocatorMemoryController(PortalStore store, CurrentUserService currentUser,
                                   LocatorMemoryService memory) {
        this.store = store;
        this.currentUser = currentUser;
        this.memory = memory;
    }

    public record EntryBody(String host, String path, String intentKey,
                            String strategy, String locator, String action) {
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(memory.list(projectId));
        } catch (Exception e) {
            return internal(e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@PathVariable String projectId, @RequestBody EntryBody body) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (body == null) return bad("body required");
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(memory.create(projectId,
                    body.host(), body.path(), body.intentKey(),
                    body.strategy(), body.locator(), body.action()));
        } catch (LocatorMemoryService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return internal(e);
        }
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable String projectId, @RequestBody EntryBody body) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (body == null) return bad("body required");
        try {
            return ResponseEntity.ok(memory.update(projectId, body.host(), body.path(),
                    body.intentKey(), body.strategy(), body.locator(), body.action()));
        } catch (LocatorMemoryService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return internal(e);
        }
    }

    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable String projectId, @RequestBody EntryBody body) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (body == null) return bad("body required");
        try {
            return ResponseEntity.ok(memory.delete(projectId, body.host(), body.path(), body.intentKey()));
        } catch (LocatorMemoryService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (IllegalArgumentException e) {
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
