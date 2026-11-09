package delivery.portal.service;

import delivery.authoring.AuthoringEngine;
import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.InvalidExcelTemplateException;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.KeelPathCounts;
import delivery.excel.KeelPathSurfaceGuard;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.job.AuthoringJobRequestFiles;
import delivery.portal.api.ApiError;
import delivery.portal.api.QualityGateResponses;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.service.credits.CreditService;
import delivery.portal.tunnel.TunnelJobBaseUrlService;
import delivery.portal.worker.ExecuteWorker;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SuiteRunService {

    private final PortalStore store;
    private final SuiteService suites;
    private final GeneratedWorkbookService workbooks;
    private final ProjectCredentialService credentials;
    private final ExecuteWorker worker;
    private final CreditService credits;
    private final StructuredPlanJobBootstrap planBootstrap;
    private final TunnelJobBaseUrlService tunnelBaseUrl;

    public SuiteRunService(
            PortalStore store,
            SuiteService suites,
            GeneratedWorkbookService workbooks,
            ProjectCredentialService credentials,
            ExecuteWorker worker,
            CreditService credits,
            StructuredPlanJobBootstrap planBootstrap,
            TunnelJobBaseUrlService tunnelBaseUrl
    ) {
        this.store = store;
        this.suites = suites;
        this.workbooks = workbooks;
        this.credentials = credentials;
        this.worker = worker;
        this.credits = credits;
        this.planBootstrap = planBootstrap;
        this.tunnelBaseUrl = tunnelBaseUrl;
    }

    public ResponseEntity<?> startRun(
            String projectId,
            String suiteId,
            Long ownerId,
            String trigger,
            String credentialProfile,
            String runner
    ) throws Exception {
        return startRun(projectId, suiteId, ownerId, trigger, credentialProfile, runner, false);
    }

    /**
     * @param preClaimed true when the caller already reserved the schedule slot ({@link SuiteService#tryClaimDue},
     *                   portal schedule worker). Schedule/monitor triggers from the API (engine poller) claim here,
     *                   so a slot can only start one run no matter how many pollers see it.
     */
    public ResponseEntity<?> startRun(
            String projectId,
            String suiteId,
            Long ownerId,
            String trigger,
            String credentialProfile,
            String runner,
            boolean preClaimed
    ) throws Exception {
        String normalized = trigger == null || trigger.isBlank() ? "manual" : trigger.trim().toLowerCase();
        boolean scheduled = "schedule".equals(normalized) || "monitor".equals(normalized);
        ResponseEntity<?> res;
        try {
            res = doStartRun(projectId, suiteId, ownerId, normalized, credentialProfile, runner, preClaimed);
        } catch (delivery.portal.service.credits.InsufficientCreditsException e) {
            res = ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(new ApiError("INSUFFICIENT_CREDITS", e.getMessage()).asMap());
            if (!scheduled) {
                throw e;
            }
        }
        if (scheduled && !res.getStatusCode().is2xxSuccessful()) {
            scheduleRefused(projectId, suiteId, normalized, res);
        }
        return res;
    }

    /** A scheduled start that did not queue a job: keep it visible on the suite and tell the bound channel. */
    private void scheduleRefused(String projectId, String suiteId, String trigger, ResponseEntity<?> res) {
        String code = "HTTP_" + res.getStatusCode().value();
        String message = String.valueOf(res.getBody());
        if (res.getBody() instanceof Map<?, ?> m) {
            if (m.get("error") != null) code = String.valueOf(m.get("error"));
            if (m.get("message") != null) message = String.valueOf(m.get("message"));
        }
        if ("SCHEDULE_NOT_DUE".equals(code)) {
            return; // another poller already started this slot — not a failure
        }
        try {
            suites.recordScheduleFailure(projectId, suiteId, code, message);
        } catch (Exception ignored) {
        }
        String channel = null;
        try {
            Object ch = suites.get(projectId, suiteId).get("alertChannelId");
            channel = ch == null ? null : String.valueOf(ch);
        } catch (Exception ignored) {
        }
        delivery.job.SuiteAlertNotifier.notifyError(
                new delivery.job.SuiteRunContext(projectId, suiteId, null, null, trigger, channel, null),
                delivery.job.SuiteAlertNotifier.RULE_SUITE_SCHEDULE_FAILED, code + " — " + message);
    }

    private ResponseEntity<?> doStartRun(
            String projectId,
            String suiteId,
            Long ownerId,
            String trigger,
            String credentialProfile,
            String runner,
            boolean preClaimed
    ) throws Exception {
        store.ensureBaseUrlBackfill(projectId);
        Optional<ProjectRecord> owned = store.getOwnedProject(projectId, ownerId);
        if (owned.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        ProjectRecord project = owned.get();
        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("PROJECT_ARCHIVED", "Cannot start execute runs on archived project").asMap());
        }
        String baseUrl = tunnelBaseUrl.effectiveBaseUrl(projectId, project.getBaseUrl());
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("MISSING_BASE_URL",
                            "Project baseUrl or active cloud tunnel is required").asMap());
        }

        Map<String, Object> suite;
        try {
            suite = suites.get(projectId, suiteId);
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadPathException e) {
            return bad(e);
        }

        @SuppressWarnings("unchecked")
        List<String> tcIds = (List<String>) suite.getOrDefault("tcIds", List.of());
        if (tcIds == null || tcIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "Suite has no tcIds to run").asMap());
        }

        boolean scheduled = "schedule".equals(trigger) || "monitor".equals(trigger);
        Map<?, ?> schedule = suite.get("schedule") instanceof Map<?, ?> sm ? sm : Map.of();
        Map<?, ?> monitor = suite.get("monitor") instanceof Map<?, ?> mm ? mm : Map.of();
        String monitorTarget = null;
        if (scheduled) {
            if (!preClaimed && suites.tryClaimDue(projectId, suiteId, java.time.Instant.now(), 0).isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("SCHEDULE_NOT_DUE",
                                "Schedule slot is not due or was already started").asMap());
            }
            // Overlap guard: a long regression must not pile up behind itself.
            Object lastJobId = schedule.get("lastJobId");
            if (lastJobId != null && !String.valueOf(lastJobId).isBlank()) {
                Optional<JobRecord> last = store.getJob(String.valueOf(lastJobId));
                if (last.isPresent() && (last.get().getStatus() == JobRecord.Status.QUEUED
                        || last.get().getStatus() == JobRecord.Status.RUNNING
                        || last.get().getStatus() == JobRecord.Status.CANCELLING)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ApiError("SKIPPED_OVERLAP", "Previous scheduled run " + lastJobId
                                    + " is still " + last.get().getStatus() + "; this slot was skipped").asMap());
                }
            }
            if (Boolean.TRUE.equals(monitor.get("enabled"))) {
                trigger = "monitor";
                monitorTarget = monitor.get("target") == null ? null : String.valueOf(monitor.get("target"));
            }
            // Scheduled runs use the profile chosen on the schedule, else a deterministic default.
            if (credentialProfile == null || credentialProfile.isBlank()) {
                Object p = schedule.get("credentialProfile");
                if (p != null && !String.valueOf(p).isBlank()) {
                    credentialProfile = String.valueOf(p);
                } else {
                    credentialProfile = credentials.list(projectId).stream()
                            .map(ProjectCredentialService.CredentialSummary::profileName)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .findFirst().orElse(null);
                }
            }
        }

        String username = "";
        String password = "";
        if (!credentials.list(projectId).isEmpty()) {
            if (credentialProfile == null || credentialProfile.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("CREDENTIAL_PROFILE_REQUIRED", "credentialProfile is required").asMap());
            }
            ProjectCredentialService.ResolvedCredential resolved =
                    credentials.resolveForJob(projectId, credentialProfile.trim());
            username = resolved.username();
            password = resolved.passwordPlain();
        }

        Path excelPath;
        try {
            excelPath = JobWorkbookResolver.resolve(workbooks, projectId, true, tcIds, null);
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK",
                                "No generated workbook for this project — generate TCs first").asMap());
            }
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        }

        try {
            List<ManualTestCase> cases = new ExcelTcReader(true).read(excelPath);
            Optional<String> block = KeelPathSurfaceGuard.hardBlock(
                    KeelPathCaseFilter.Surface.EXECUTE, KeelPathCounts.from(cases));
            if (block.isPresent()) {
                Files.deleteIfExists(excelPath);
                return ResponseEntity.badRequest()
                        .body(new ApiError("SURFACE_MISMATCH", block.get()).asMap());
            }
            List<String> gateErrors = GenerateQualityGate.validate(
                    TcImportRepair.repairCases(cases), baseUrl);
            if (!gateErrors.isEmpty()) {
                Files.deleteIfExists(excelPath);
                return ResponseEntity.badRequest()
                        .body(QualityGateResponses.fromErrors(gateErrors));
            }
        } catch (InvalidExcelTemplateException e) {
            Files.deleteIfExists(excelPath);
            throw e;
        }

        String jobId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AuthoringEngine engine = store.authoringEngineForProject(projectId);
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                ownerId,
                "EXECUTE",
                excelPath,
                baseUrl,
                username == null ? "" : username,
                password == null ? "" : password,
                false,
                JobRecord.JobKind.EXECUTE
        );
        job.setAuthoringEngine(engine);
        workbooks.headRevisionId(projectId).ifPresent(job::setLibraryRevisionId);
        Path requestPath = AuthoringJobRequestFiles.requestPath(
                store.projectDiskRoot(projectId),
                delivery.portal.engine.InProcessJobContext.wire(JobRecord.JobKind.EXECUTE),
                jobId);
        AuthoringJobRequestFiles.write(requestPath, engine);
        // The run executes on the async worker thread: persist the suite context so it can be restored there
        // (alerts to the bound channel, monitor target URL, trigger).
        Object alertChannel = suite.get("alertChannelId");
        Object alertMinSev = suite.get("alertMinSeverity");
        delivery.job.SuiteRunContextFiles.write(requestPath.getParent(), new delivery.job.SuiteRunContext(
                projectId, suiteId, null, tcIds, trigger,
                alertChannel == null ? null : String.valueOf(alertChannel),
                alertMinSev == null ? null : String.valueOf(alertMinSev),
                monitorTarget));
        if (runner != null && "private".equalsIgnoreCase(runner.trim())) {
            job.setRequirePrivateRunner(true);
        }

        String normalizedTrigger = trigger;
        if ("schedule".equals(normalizedTrigger) || "monitor".equals(normalizedTrigger)) {
            credits.tryDebitForAddon(
                    ownerId, CreditService.ADDON_SUITES_MONITOR, projectId + ":" + suiteId + ":" + jobId);
        }

        store.saveJob(job);
        Object planRef = suite.get("structuredPlanId");
        if (planRef != null && !String.valueOf(planRef).isBlank()) {
            planBootstrap.attachPlanSnapshot(projectId, jobId, String.valueOf(planRef));
        }
        if (!job.isRequirePrivateRunner()) {
            worker.submit(jobId);
        }

        // Only scheduled/monitor runs touch the schedule. A manual run used to overwrite lastJobId and push
        // nextRunAt, delaying interval schedules every time someone pressed Run.
        if (scheduled) {
            try {
                suites.recordFire(projectId, suiteId, jobId);
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jobId", jobId);
        resp.put("status", JobRecord.Status.QUEUED.name());
        resp.put("suiteId", suiteId);
        resp.put("trigger", normalizedTrigger);
        resp.put("source", "suite");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }

    private static ResponseEntity<?> bad(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
    }

    private static ResponseEntity<?> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
    }
}
