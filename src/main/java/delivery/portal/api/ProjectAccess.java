package delivery.portal.api;

import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class ProjectAccess {
    private ProjectAccess() {
    }

    static ResponseEntity<?> denyUnlessReadable(PortalStore store, String projectId, Long userId) {
        if (store.getOwnedProject(projectId, userId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        return null;
    }

    static ResponseEntity<?> denyUnlessOperable(PortalStore store, String projectId, Long userId) {
        ResponseEntity<?> read = denyUnlessReadable(store, projectId, userId);
        if (read != null) {
            return read;
        }
        if (!store.canOperate(projectId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("ROLE_REQUIRED", "Member role cannot mutate, execute, or export").asMap());
        }
        return null;
    }

    static ResponseEntity<?> denyUnlessAdmin(PortalStore store, String projectId, Long userId) {
        ResponseEntity<?> read = denyUnlessReadable(store, projectId, userId);
        if (read != null) {
            return read;
        }
        if (!store.canAdminister(projectId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("ROLE_REQUIRED", "Only the workspace owner can administer this project").asMap());
        }
        return null;
    }
}
