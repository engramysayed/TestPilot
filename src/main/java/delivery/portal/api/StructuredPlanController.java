package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.StructuredPlanDiskStore;
import delivery.portal.service.StructuredPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/structured-plans")
public class StructuredPlanController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final StructuredPlanService plans;

    public StructuredPlanController(PortalStore store, CurrentUserService currentUser, StructuredPlanService plans) {
        this.store = store;
        this.currentUser = currentUser;
        this.plans = plans;
    }

    public record StepBody(String id, String type, String text) {
    }

    public record PlanBody(String id, String name, List<StepBody> steps, Integer expectedRevision) {
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(plans.list(projectId));
        } catch (Exception e) {
            return internal(e);
        }
    }

    @GetMapping("/{planId}")
    public ResponseEntity<?> get(@PathVariable String projectId, @PathVariable String planId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return plans.get(projectId, planId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Plan not found").asMap()));
        } catch (Exception e) {
            return internal(e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@PathVariable String projectId, @RequestBody(required = false) PlanBody body) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        if (body == null || body.id() == null || body.id().isBlank()) {
            return bad("id is required");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(plans.create(
                    projectId, body.id(), body.name(), steps(body.steps())));
        } catch (StructuredPlanDiskStore.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", e.getMessage()).asMap());
        } catch (StructuredPlanDiskStore.BadRequestException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return internal(e);
        }
    }

    @PutMapping(value = "/{planId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String projectId,
            @PathVariable String planId,
            @RequestBody(required = false) PlanBody body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        try {
            return ResponseEntity.ok(plans.update(
                    projectId, planId,
                    body == null ? null : body.name(),
                    steps(body == null ? null : body.steps()),
                    body == null ? null : body.expectedRevision()));
        } catch (StructuredPlanDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (StructuredPlanDiskStore.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("REVISION_CONFLICT", e.getMessage()).asMap());
        } catch (StructuredPlanDiskStore.BadRequestException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return internal(e);
        }
    }

    private static List<Map<String, Object>> steps(List<StepBody> steps) {
        if (steps == null) return List.of();
        return steps.stream().map(s -> Map.<String, Object>of(
                "id", s.id() == null ? "" : s.id(),
                "type", s.type() == null ? "" : s.type(),
                "text", s.text() == null ? "" : s.text())).toList();
    }

    private static ResponseEntity<?> bad(String msg) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", msg).asMap());
    }

    private static ResponseEntity<?> internal(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", e.getMessage() == null ? "error" : e.getMessage()).asMap());
    }
}
