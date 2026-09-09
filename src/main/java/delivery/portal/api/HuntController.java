package delivery.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ManualTestCase;
import delivery.hunt.HuntBriefBuilder;
import delivery.hunt.HuntRequest;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.worker.HuntWorker;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class HuntController {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final GeneratedWorkbookService workbooks;
    private final ProjectCredentialService credentials;
    private final HuntWorker worker;
    private final DeliveryPortalProperties props;

    public HuntController(PortalStore store, CurrentUserService currentUser,
                          GeneratedWorkbookService workbooks,
                          ProjectCredentialService credentials,
                          HuntWorker worker,
                          DeliveryPortalProperties props) {
        this.store = store;
        this.currentUser = currentUser;
        this.workbooks = workbooks;
        this.credentials = credentials;
        this.worker = worker;
        this.props = props;
    }

    public record StartHuntBody(
            List<String> tcIds,
            String userStory,
            String planner,
            Integer scenarioCap,
            Integer cycleCeiling,
            Integer actionCapPerCycle,
            String credentialProfile,
            String domMode,
            Boolean strategiesEnabled
    ) {
    }

    @PostMapping(value = "/{projectId}/hunt-runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> startHunt(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) StartHuntBody body
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        store.ensureBaseUrlBackfill(projectId);
        ProjectRecord project = store.getOwnedProject(projectId, ownerId).orElseThrow();
        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("PROJECT_ARCHIVED", "Cannot start Bug Hunter on archived project").asMap());
        }
        String baseUrl = project.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("MISSING_BASE_URL", "Project baseUrl is required").asMap());
        }
        if (!workbooks.hasWorkbook(projectId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NO_GENERATED_WORKBOOK",
                            "No generated workbook for this project — generate or upload TCs first").asMap());
        }
        StartHuntBody req = body == null
                ? new StartHuntBody(List.of(), "", "ollama", null, null, null, null, null, null)
                : body;
        List<String> tcIds = req.tcIds() == null ? List.of() : req.tcIds();
        if (tcIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("TC_IDS_REQUIRED", "Select at least one library test case").asMap());
        }
        List<ManualTestCase> all = workbooks.readCases(projectId);
        List<ManualTestCase> selected = HuntBriefBuilder.filterSelected(all, tcIds);
        if (selected.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("TC_IDS_UNKNOWN", "None of the selected TC IDs exist in the library").asMap());
        }

        String username = "";
        String password = "";
        if (!credentials.list(projectId).isEmpty()) {
            if (req.credentialProfile() == null || req.credentialProfile().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("CREDENTIAL_PROFILE_REQUIRED", "credentialProfile is required").asMap());
            }
            ProjectCredentialService.ResolvedCredential resolved =
                    credentials.resolveForJob(projectId, req.credentialProfile().trim());
            username = resolved.username();
            password = resolved.passwordPlain();
        }

        String jobId = "hunt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        HuntRequest hunt = new HuntRequest();
        hunt.setProjectId(projectId);
        hunt.setJobId(jobId);
        hunt.setBaseUrl(baseUrl);
        hunt.setTcIds(selected.stream().map(ManualTestCase::tcId).toList());
        hunt.setUserStory(req.userStory());
        hunt.setPlanner(req.planner());
        if (req.scenarioCap() != null) {
            hunt.setScenarioCap(req.scenarioCap());
        }
        if (req.cycleCeiling() != null) {
            hunt.setCycleCeiling(req.cycleCeiling());
        }
        if (req.actionCapPerCycle() != null) {
            hunt.setActionCapPerCycle(req.actionCapPerCycle());
        }
        if (req.domMode() != null && !req.domMode().isBlank()) {
            hunt.setDomMode(req.domMode());
        } else {
            hunt.setDomMode(props.getHunt().getDomMode());
        }
        if (req.strategiesEnabled() != null) {
            hunt.setStrategiesEnabled(req.strategiesEnabled());
        }
        hunt.normalize();

        Path huntDir = store.projectDiskRoot(projectId).resolve("hunt-runs").resolve(jobId);
        Files.createDirectories(huntDir);
        Path requestPath = huntDir.resolve("request.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(requestPath.toFile(), hunt);

        JobRecord job = new JobRecord(
                jobId,
                projectId,
                ownerId,
                "HUNT",
                requestPath,
                baseUrl,
                username == null ? "" : username,
                password == null ? "" : password,
                false,
                JobRecord.JobKind.HUNT
        );
        job.setGenerateModel(hunt.getPlanner());
        job.setProgressTotal(hunt.getCycleCeiling());
        store.saveJob(job);
        worker.submit(jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", JobRecord.Status.QUEUED.name(),
                "jobKind", JobRecord.JobKind.HUNT.name(),
                "scenarioCap", hunt.getScenarioCap(),
                "cycleCeiling", hunt.getCycleCeiling(),
                "actionCapPerCycle", hunt.getActionCapPerCycle()
        ));
    }
}
