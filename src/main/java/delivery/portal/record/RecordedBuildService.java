package delivery.portal.record;

import delivery.authoring.AuthoringEngine;
import delivery.job.RecordedCapture;
import delivery.job.RecordedCaptureFile;
import delivery.job.RecordedDrafts;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.roles.PermissionEnforcer;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.JobWorkbookResolver;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.worker.ConversionWorker;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Starts the existing framework builder for one recorded case. The worker sees the sidecar and skips the browser.
 */
@Service
public class RecordedBuildService {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final PermissionEnforcer permissionEnforcer;
    private final ProjectCredentialService credentials;
    private final GeneratedWorkbookService workbooks;
    private final DeliveryPortalProperties props;
    private final ConversionWorker worker;

    public RecordedBuildService(
            PortalStore store,
            CurrentUserService currentUser,
            PermissionEnforcer permissionEnforcer,
            ProjectCredentialService credentials,
            GeneratedWorkbookService workbooks,
            DeliveryPortalProperties props,
            ConversionWorker worker
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.permissionEnforcer = permissionEnforcer;
        this.credentials = credentials;
        this.workbooks = workbooks;
        this.props = props;
        this.worker = worker;
    }

    public String start(String projectId, String tcId, List<RecordingLog.Capture> captures) throws Exception {
        permissionEnforcer.requireFeatureJob(currentUser.requireUser(), "AUTOMATE");
        List<RecordedCapture> recorded = toJob(captures);
        if (!RecordedDrafts.canBuild(recorded)) {
            throw new IllegalArgumentException(
                    "No stable locators or checks were recorded, so the framework was not built. The test case is saved.");
        }
        Long ownerId = currentUser.requireUserId();
        ProjectRecord project = store.getOwnedProject(projectId, ownerId).orElseThrow();
        String baseUrl = project.getBaseUrl() == null ? "" : project.getBaseUrl();
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("Set the project website before building the framework.");
        }
        String username = "";
        String password = "";
        var profiles = credentials.list(projectId);
        if (profiles != null && !profiles.isEmpty()) {
            var resolved = credentials.resolveForJob(projectId, profiles.get(0).profileName());
            username = resolved.username() == null ? "" : resolved.username();
            password = resolved.passwordPlain() == null ? "" : resolved.passwordPlain();
        }
        boolean update = store.hasStoredFramework(projectId);
        Path excelPath = JobWorkbookResolver.resolve(workbooks, projectId, true, List.of(tcId), null);
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path sidecar = RecordedCaptureFile.path(Path.of(props.getWorkDir()), jobId);
        RecordedCaptureFile.write(sidecar, recorded);
        AuthoringEngine engine = store.authoringEngineForProject(projectId);
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                ownerId,
                update ? "UPDATE" : "NEW",
                excelPath,
                baseUrl,
                username,
                password,
                false,
                JobRecord.JobKind.CONVERT
        );
        job.setAuthoringEngine(engine);
        job.setMessage("Building the framework from the recording");
        workbooks.headRevisionId(projectId).ifPresent(job::setLibraryRevisionId);
        try {
            store.saveJob(job);
        } catch (RuntimeException e) {
            java.nio.file.Files.deleteIfExists(sidecar);
            throw e;
        }
        worker.submit(jobId);
        return jobId;
    }

    private static List<RecordedCapture> toJob(List<RecordingLog.Capture> captures) {
        List<RecordedCapture> out = new ArrayList<>();
        if (captures == null) {
            return out;
        }
        for (RecordingLog.Capture capture : captures) {
            if (capture == null) {
                continue;
            }
            out.add(new RecordedCapture(
                    capture.kind(), capture.label(), capture.value(), capture.secret(),
                    capture.strategy(), capture.locator(), capture.pageUrl()));
        }
        return out;
    }
}
