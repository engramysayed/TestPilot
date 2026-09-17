package delivery.portal.api;

import delivery.job.RerunSupport;
import delivery.portal.model.JobRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.worker.ConversionWorker;
import delivery.portal.worker.ExecuteWorker;
import delivery.portal.worker.HuntWorker;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobRerunController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final ConversionWorker conversionWorker;
    private final ExecuteWorker executeWorker;
    private final HuntWorker huntWorker;

    public JobRerunController(
            PortalStore store,
            CurrentUserService currentUser,
            ConversionWorker conversionWorker,
            ExecuteWorker executeWorker,
            HuntWorker huntWorker
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.conversionWorker = conversionWorker;
        this.executeWorker = executeWorker;
        this.huntWorker = huntWorker;
    }

    @PostMapping("/{jobId}/rerun")
    public ResponseEntity<?> rerun(@PathVariable("jobId") String jobId) throws Exception {
        Long uid = currentUser.requireUserId();
        JobRecord source = store.getOwnedJob(jobId, uid).orElse(null);
        if (source == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, source.getProjectId(), uid);
        if (denied != null) {
            return denied;
        }
        JobRecord copy = RerunSupport.newAttempt(source);
        store.saveJob(copy);
        switch (copy.getJobKind()) {
            case EXECUTE -> executeWorker.submit(copy.getJobId());
            case HUNT -> huntWorker.submit(copy.getJobId());
            default -> conversionWorker.submit(copy.getJobId());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", copy.getJobId(),
                "parentJobId", copy.getParentJobId(),
                "status", copy.getStatus().name(),
                "libraryRevisionId", copy.getLibraryRevisionId(),
                "environmentRevisionId", copy.getEnvironmentRevisionId()
        ));
    }
}
