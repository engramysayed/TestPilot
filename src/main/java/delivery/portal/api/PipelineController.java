package delivery.portal.api;

import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PipelineService;
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

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PipelineController {

    public record PipelineStartRequest(String stories) {}

    private final PortalStore store;
    private final PipelineService pipelines;
    private final CurrentUserService currentUser;

    public PipelineController(PortalStore store, PipelineService pipelines, CurrentUserService currentUser) {
        this.store = store;
        this.pipelines = pipelines;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/projects/{projectId}/pipeline", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> start(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) PipelineStartRequest body
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        ProjectRecord project = store.getOwnedProject(projectId, ownerId).orElse(null);
        if (project == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("PROJECT_ARCHIVED", "Cannot start pipeline on archived project").asMap());
        }

        String stories = body == null ? null : body.stories();
        try {
            Map<String, Object> started = pipelines.start(projectId, ownerId, stories);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("pipelineId", started.get("pipelineId"));
            out.put("status", started.get("status"));
            out.put("stages", started.get("stages"));
            out.put("statusUrl", "/api/pipelines/" + started.get("pipelineId"));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(out);
        } catch (IllegalStateException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("NO_GENERATED_WORKBOOK")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK",
                                "No generated workbook — paste stories or generate TCs first").asMap());
            }
            if (msg.contains("SURFACE_MISMATCH")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiError("SURFACE_MISMATCH", msg).asMap());
            }
            throw e;
        }
    }

    @GetMapping("/pipelines/{pipelineId}")
    public ResponseEntity<?> status(@PathVariable("pipelineId") String pipelineId) throws Exception {
        Long ownerId = currentUser.requireUserId();
        try {
            return ResponseEntity.ok(pipelines.get(pipelineId, ownerId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown pipeline").asMap());
        }
    }
}
