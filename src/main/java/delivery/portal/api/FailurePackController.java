package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.FailurePackService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/jobs/{jobId}")
public class FailurePackController {

    private final FailurePackService packs;
    private final CurrentUserService currentUser;

    public FailurePackController(FailurePackService packs, CurrentUserService currentUser) {
        this.packs = packs;
        this.currentUser = currentUser;
    }

    @GetMapping("/failure-pack")
    public ResponseEntity<?> download(
            @PathVariable("projectId") String projectId,
            @PathVariable("jobId") String jobId
    ) {
        Long uid = currentUser.requireUserId();
        FailurePackService.Eligibility e = packs.eligibility(projectId, jobId, uid);
        if (e == FailurePackService.Eligibility.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        if (e == FailurePackService.Eligibility.NOT_ELIGIBLE) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FAILED", "Failure pack is only for failed jobs").asMap());
        }
        try {
            FailurePackService.PackPaths built = packs.buildOrReuse(projectId, jobId, uid);
            Resource resource = new FileSystemResource(built.zip());
            String filename = "keel-failure-" + jobId + ".zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(resource);
        } catch (FailurePackService.NotEligibleException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FAILED", ex.getMessage()).asMap());
        } catch (Exception ex) {
            Throwable root = ex;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String msg = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiError("PACK_BUILD_FAILED", msg).asMap());
        }
    }
}
