package delivery.portal.service;

import delivery.authoring.AuthoringEngine;
import delivery.mobile.model.ProjectPlatform;
import delivery.authoring.PrecisionJobConfig;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.alerts.AttentionAlertSender;
import delivery.portal.model.JobRecord;
import delivery.portal.model.PatchProjectRequest;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.JobSecretCrypto;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectCredentialRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import delivery.store.ArtifactResolver;
import delivery.store.DomainStorePaths;
import delivery.store.GeneratedStoreLayout;
import delivery.store.LibraryRevisionStore;
import delivery.store.PreferredHooksStore;
import delivery.store.ProjectStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class PortalStore {
    private static final DateTimeFormatter WHEN = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Map<String, JobRecord> jobs = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicBoolean> cancelRequested = new ConcurrentHashMap<>();
    private final DeliveryPortalProperties portalProperties;
    private final Path storeRootPath;
    private final ProjectStore projectStore;
    private final ProjectRepository projectRepository;
    private final JobRepository jobRepository;
    private final ProjectCredentialRepository credentialRepository;
    private final ObjectProvider<WebhookDispatcher> webhooks;
    private AccountAlertService attentionAlerts;

    public PortalStore(DeliveryPortalProperties props,
                       ProjectRepository projectRepository,
                       JobRepository jobRepository) {
        this(props, projectRepository, jobRepository, null, null);
    }

    public PortalStore(DeliveryPortalProperties props,
                       ProjectRepository projectRepository,
                       JobRepository jobRepository,
                       ProjectCredentialRepository credentialRepository) {
        this(props, projectRepository, jobRepository, credentialRepository, null);
    }

    @Autowired
    public PortalStore(DeliveryPortalProperties props,
                       ProjectRepository projectRepository,
                       JobRepository jobRepository,
                       ProjectCredentialRepository credentialRepository,
                       ObjectProvider<WebhookDispatcher> webhooks) {
        this.portalProperties = props;
        this.storeRootPath = java.nio.file.Path.of(props.getStoreRoot());
        this.projectStore = new ProjectStore(storeRootPath);
        this.projectRepository = projectRepository;
        this.jobRepository = jobRepository;
        this.credentialRepository = credentialRepository;
        this.webhooks = webhooks;
    }

    private delivery.portal.jobs.JobDispatchPolicy dispatchPolicy;
    private delivery.portal.service.credits.CreditService creditService;
    private delivery.portal.service.credits.AccountApprovalService accountApprovals;

    /** B08: per-kind dispatch (inprocess | remote). Optional so plain unit-test stores keep today's behaviour. */
    @Autowired(required = false)
    public void setDispatchPolicy(delivery.portal.jobs.JobDispatchPolicy dispatchPolicy) {
        this.dispatchPolicy = dispatchPolicy;
    }

    /** Account alerts when a run fails or finishes with a block. Optional for unit-test stores. */
    @Autowired(required = false)
    public void setAttentionAlerts(AccountAlertService attentionAlerts) {
        this.attentionAlerts = attentionAlerts;
    }

    /** Keel Credits Phase 1–2: optional so unit-test stores without credit beans keep working. */
    @Autowired(required = false)
    public void setCreditService(delivery.portal.service.credits.CreditService creditService) {
        this.creditService = creditService;
    }

    @Autowired(required = false)
    public void setAccountApprovals(delivery.portal.service.credits.AccountApprovalService accountApprovals) {
        this.accountApprovals = accountApprovals;
    }

    /** B08: issues the next fencing token for a claimed job and persists it before it is returned to the engine. */
    public synchronized long issueFencingToken(String jobId) {
        JobRecord job = getJob(jobId).orElseThrow(() -> new IllegalArgumentException("unknown job"));
        long next = Math.max(job.getFencingToken(),
                jobRepository.findByJobId(jobId).map(JobEntity::getFencingToken).orElse(0L)) + 1;
        job.setFencingToken(next);
        syncJobPersistence(job);
        return next;
    }

    /** B08: current persisted fencing token (0 if never issued). */
    public synchronized long currentFencingToken(String jobId) {
        return jobRepository.findByJobId(jobId).map(JobEntity::getFencingToken)
                .orElseGet(() -> getJob(jobId).map(JobRecord::getFencingToken).orElse(0L));
    }

    public AuthoringEngine authoringEngineForProject(String projectId) {
        return projectRepository.findByProjectId(projectId)
                .map(e -> AuthoringEngine.parse(e.getAuthoringEngine()))
                .orElse(AuthoringEngine.KEEL);
    }

    public PrecisionJobConfig precisionConfigForProject(String projectId) {
        AuthoringEngine engine = authoringEngineForProject(projectId);
        Integer projectMax = projectRepository.findByProjectId(projectId)
                .map(ProjectEntity::getPrecisionMaxCallsPerJob)
                .orElse(null);
        int max = projectMax != null && projectMax > 0
                ? projectMax
                : portalProperties.getPrecisionMaxCallsPerJob();
        boolean enabled = portalProperties.isPrecisionAuthoringEnabled()
                && engine == AuthoringEngine.PRECISION;
        boolean reuse = projectRepository.findByProjectId(projectId)
                .map(e -> Boolean.TRUE.equals(e.getPrecisionReuseLocatorMemory()))
                .orElse(false);
        return new PrecisionJobConfig(enabled, max, reuse);
    }

    public PrecisionJobConfig precisionConfigForJob(JobRecord job) {
        boolean enabled = job.getPrecisionEnabledSnapshot() != null
                ? job.getPrecisionEnabledSnapshot()
                : portalProperties.isPrecisionAuthoringEnabled()
                  && job.getAuthoringEngine() == AuthoringEngine.PRECISION;
        boolean reuse = job.getPrecisionReuseLocatorMemorySnapshot() != null
                ? job.getPrecisionReuseLocatorMemorySnapshot()
                : projectRepository.findByProjectId(job.getProjectId())
                .map(e -> Boolean.TRUE.equals(e.getPrecisionReuseLocatorMemory()))
                .orElse(false);
        return new PrecisionJobConfig(enabled, job.getPrecisionMaxSnapshot() > 0
                ? job.getPrecisionMaxSnapshot() : portalProperties.getPrecisionMaxCallsPerJob(), reuse);
    }

    public ProjectStore filesystemStore() {
        return projectStore;
    }

    public ProjectStore filesystemStoreFor(String projectId) {
        ProjectEntity entity = projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalStateException("TENANT_REQUIRED"));
        delivery.identity.TenantId tenant = resolveTenant(entity);
        if (tenant == null) {
            throw new IllegalStateException("TENANT_REQUIRED");
        }
        return new ProjectStore(storeRootPath, resolveBaseUrlHint(projectId), tenant);
    }

    private delivery.identity.TenantId resolveTenant(ProjectEntity entity) {
        if (entity == null) {
            return null;
        }
        String stored = entity.getTenantId();
        if (stored != null && !stored.isBlank()) {
            try {
                return delivery.identity.TenantId.parse(stored);
            } catch (IllegalArgumentException ignored) {
                // fall through to directory bootstrap
            }
        }
        Long owner = entity.getOwnerUserId();
        if (owner == null || owner <= 0) {
            return null;
        }
        try {
            delivery.identity.TenantId tenant = delivery.identity.WorkspaceDirectory
                    .open(storeRootPath)
                    .ensurePersonalWorkspace(owner);
            if (entity.getTenantId() == null || entity.getTenantId().isBlank()) {
                entity.setTenantId(tenant.value());
                projectRepository.save(entity);
            }
            return tenant;
        } catch (Exception e) {
            return null;
        }
    }

    public Path projectDiskRoot(String projectId) {
        return filesystemStoreFor(projectId).projectRoot(projectId);
    }

    public Path storeRoot() {
        return storeRootPath;
    }

    private String resolveBaseUrlHint(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return projectRepository.findByProjectId(projectId)
                .map(ProjectEntity::getBaseUrl)
                .filter(u -> u != null && !u.isBlank())
                .orElseGet(() -> latestBaseUrlHint(projectId));
    }

    private String latestBaseUrlHint(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        return jobRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(JobEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(JobEntity::getBaseUrl)
                .filter(u -> u != null && !u.isBlank())
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public ProjectRecord createProject(String name, Long ownerUserId) {
        return createProject(name, null, ownerUserId, ProjectPlatform.WEB);
    }

    public ProjectRecord createProject(String name, String baseUrlHint, Long ownerUserId) {
        return createProject(name, baseUrlHint, ownerUserId, ProjectPlatform.WEB);
    }

    @Transactional
    public ProjectRecord createProject(String name, String baseUrlHint, Long ownerUserId, ProjectPlatform platform) {
        String id = "prj_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ProjectEntity entity = new ProjectEntity();
        entity.setProjectId(id);
        String display;
        if (name != null && !name.isBlank()) {
            display = name.trim();
        } else if (baseUrlHint != null && !baseUrlHint.isBlank()) {
            display = delivery.util.ProjectNaming.hostSlug(baseUrlHint);
        } else {
            display = "Project";
        }
        entity.setName(display);
        entity.setOwnerUserId(ownerUserId);
        entity.setLatestVersion(0);
        entity.setPlatform((platform == null ? ProjectPlatform.WEB : platform).name());
        if (baseUrlHint != null && !baseUrlHint.isBlank()) {
            entity.setBaseUrl(baseUrlHint.trim());
        }
        if (ownerUserId != null && ownerUserId > 0) {
            try {
                entity.setTenantId(delivery.identity.WorkspaceDirectory
                        .open(storeRootPath)
                        .ensurePersonalWorkspace(ownerUserId)
                        .value());
            } catch (Exception ignored) {
                // tenant bootstrap is best-effort; filesystem still dual-reads
            }
        }
        projectRepository.save(entity);
        return toRecord(entity);
    }

    /**
     * Platform is immutable after create; conversion requires a new project.
     * @throws IllegalArgumentException when a change is requested
     */
    public void rejectPlatformMutation(String projectId, String requestedPlatform) {
        if (requestedPlatform == null || requestedPlatform.isBlank()) {
            return;
        }
        ProjectPlatform want = ProjectPlatform.parse(requestedPlatform);
        ProjectPlatform have = projectRepository.findByProjectId(projectId)
                .map(e -> ProjectPlatform.parse(e.getPlatform()))
                .orElse(ProjectPlatform.WEB);
        if (want != have) {
            throw new IllegalArgumentException("platform is immutable after create");
        }
    }

    public Optional<ProjectRecord> getProject(String projectId) {
        return projectRepository.findByProjectId(projectId).map(this::toRecord);
    }

    /** Locator ranking mode stored next to the preferred hooks for this project's site. */
    public String locatorModeWire(String projectId) {
        try {
            return PreferredHooksStore.loadMode(
                    storeRootPath, tenantForProject(projectId), resolveBaseUrlHint(projectId)).wire();
        } catch (RuntimeException e) {
            return delivery.locator.LocatorMode.STABLE_FIRST.wire();
        }
    }

    public String preferredHooksJoined(String projectId) {
        return PreferredHooksStore.join(PreferredHooksStore.load(
                storeRootPath, tenantForProject(projectId), resolveBaseUrlHint(projectId)));
    }

    public Optional<ProjectRecord> getOwnedProject(String projectId, Long ownerUserId) {
        return projectRepository.findByProjectId(projectId)
                .filter(p -> canAccessProject(p, ownerUserId))
                .map(this::toRecord);
    }

    private boolean canAccessProject(ProjectEntity project, Long userId) {
        if (project == null || userId == null) {
            return false;
        }
        String tenantId = project.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                return delivery.identity.WorkspaceDirectory.open(storeRootPath)
                        .isMember(delivery.identity.TenantId.parse(tenantId), userId);
            } catch (Exception e) {
                return project.getOwnerUserId().equals(userId);
            }
        }
        return project.getOwnerUserId().equals(userId);
    }

    public List<ProjectRecord> listProjects(Long ownerUserId) {
        return listProjects(ownerUserId, false);
    }

    public List<ProjectRecord> listProjects(Long ownerUserId, boolean includeArchived) {
        return projectRepository.findAll().stream()
                .filter(p -> canAccessProject(p, ownerUserId))
                .filter(p -> includeArchived || !p.isArchived())
                .sorted(Comparator.comparing(ProjectEntity::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    /** Internal schedule worker: all non-archived projects (no user filter). */
    public List<ProjectRecord> listProjectsForScheduleDispatch() {
        return projectRepository.findAll().stream()
                .filter(p -> !p.isArchived())
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    /**
     * Runner-authed schedule dispatch: non-archived projects in the runner's own tenant only.
     * A runner enrolled in tenant A must not see tenant B's suites.
     */
    public List<ProjectRecord> listProjectsForScheduleDispatchByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return java.util.List.of();
        }
        return projectRepository.findAll().stream()
                .filter(p -> !p.isArchived())
                .filter(p -> tenantId.equals(p.getTenantId()))
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<ProjectRecord> updateOwnedProject(String projectId, Long ownerUserId, PatchProjectRequest patch) {
        Optional<ProjectEntity> owned = projectRepository.findByProjectId(projectId)
                .filter(p -> canAccessProject(p, ownerUserId));
        if (owned.isEmpty()) {
            return Optional.empty();
        }
        ProjectEntity entity = owned.get();
        ensureBaseUrlBackfill(projectId);
        if (patch.name() != null && !patch.name().isBlank()) {
            entity.setName(patch.name().trim());
        }
        if (patch.baseUrl() != null) {
            entity.setBaseUrl(patch.baseUrl().isBlank() ? null : patch.baseUrl().trim());
        }
        if (patch.archived() != null) {
            entity.setArchived(patch.archived());
            entity.setArchivedAt(patch.archived() ? Instant.now() : null);
        }
        if (patch.preferredHooks() != null) {
            String url = entity.getBaseUrl() != null && !entity.getBaseUrl().isBlank()
                    ? entity.getBaseUrl()
                    : resolveBaseUrlHint(projectId);
            PreferredHooksStore.save(storeRootPath, tenantForProject(projectId), url, patch.preferredHooks());
        }
        if (patch.locatorMode() != null && !patch.locatorMode().isBlank()) {
            String url = entity.getBaseUrl() != null && !entity.getBaseUrl().isBlank()
                    ? entity.getBaseUrl()
                    : resolveBaseUrlHint(projectId);
            PreferredHooksStore.saveMode(storeRootPath, tenantForProject(projectId), url, patch.locatorMode());
        }
        if (patch.authoringEngine() != null) {
            entity.setAuthoringEngine(AuthoringEngine.parse(patch.authoringEngine()).wireValue());
        }
        if (patch.precisionMaxCallsPerJob() != null) {
            int max = patch.precisionMaxCallsPerJob();
            entity.setPrecisionMaxCallsPerJob(max <= 0 ? null : Math.min(max, 500));
        }
        if (patch.precisionReuseLocatorMemory() != null) {
            entity.setPrecisionReuseLocatorMemory(patch.precisionReuseLocatorMemory());
        }
        if (patch.testDataEnv() != null) {
            String env = patch.testDataEnv().trim().toLowerCase(Locale.ROOT);
            entity.setTestDataEnv(env.isBlank() ? null : env.replaceAll("[^a-z0-9_-]", ""));
            try {
                delivery.codegen.CodegenEnvHint.write(
                        filesystemStoreFor(projectId).projectRoot(projectId),
                        entity.getTestDataEnv() == null ? "" : entity.getTestDataEnv());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(PortalStore.class)
                        .warn("Could not persist test-data env hint for {}: {}", projectId, e.getMessage());
            }
        }
        projectRepository.save(entity);
        return Optional.of(toRecord(entity));
    }

    @Transactional
    public void ensureBaseUrlBackfill(String projectId) {
        projectRepository.findByProjectId(projectId).ifPresent(entity -> {
            if (entity.getBaseUrl() != null && !entity.getBaseUrl().isBlank()) {
                return;
            }
            String hint = latestBaseUrlHint(projectId);
            if (hint != null && !hint.isBlank()) {
                entity.setBaseUrl(hint);
                projectRepository.save(entity);
            }
        });
    }

    public boolean hasRunningJob(String projectId) {
        boolean fromDb = jobRepository.findByProjectId(projectId).stream()
                .anyMatch(j -> JobRecord.Status.QUEUED.name().equals(j.getStatus())
                        || JobRecord.Status.RUNNING.name().equals(j.getStatus()));
        if (fromDb) {
            return true;
        }
        return jobs.values().stream()
                .filter(j -> projectId.equals(j.getProjectId()))
                .anyMatch(j -> j.getStatus() == JobRecord.Status.QUEUED
                        || j.getStatus() == JobRecord.Status.RUNNING);
    }

    /** Best-effort last conversion / job activity for a project. */
    public Optional<Instant> lastActivity(String projectId) {
        Instant fromJobs = jobRepository.findByProjectId(projectId).stream()
                .map(j -> j.getCompletedAt() != null ? j.getCompletedAt() : j.getCreatedAt())
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant fromFile = null;
        try {
            Path lastJob = projectDiskRoot(projectId).resolve("last-job.json");
            if (Files.isRegularFile(lastJob)) {
                JSONObject json = new JSONObject(Files.readString(lastJob, StandardCharsets.UTF_8));
                String at = json.optString("completedAt", "");
                if (!at.isBlank()) {
                    fromFile = Instant.parse(at);
                } else {
                    fromFile = Files.getLastModifiedTime(lastJob).toInstant();
                }
            }
        } catch (Exception ignored) {
        }
        if (fromJobs == null) {
            return Optional.ofNullable(fromFile);
        }
        if (fromFile == null) {
            return Optional.of(fromJobs);
        }
        return Optional.of(fromJobs.isAfter(fromFile) ? fromJobs : fromFile);
    }

    @Transactional
    public void updateProjectVersion(String projectId, int version) {
        projectRepository.findByProjectId(projectId).ifPresent(p -> {
            p.setLatestVersion(version);
            projectRepository.save(p);
        });
    }

    /**
     * Workspace-owner delete: tombstone, stop workers, then purge credentials, jobs, and disk.
     * Partial disk failure is logged and retryable because the DB row is already gone.
     */
    @Transactional
    public boolean deleteOwnedProject(String projectId, Long ownerUserId) {
        if (!canAdminister(projectId, ownerUserId)) {
            return false;
        }
        return projectRepository.findByProjectId(projectId)
                .map(this::purgeProject)
                .orElse(false);
    }

    /**
     * Admin / lifecycle purge that does not require the acting caller to own the project.
     * Same tombstone → stop → credentials → jobs → disk sequence as owner delete.
     */
    @Transactional
    public boolean purgeProjectById(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return false;
        }
        return projectRepository.findByProjectId(projectId)
                .map(this::purgeProject)
                .orElse(false);
    }

    private synchronized boolean purgeProject(ProjectEntity entity) {
        String projectId = entity.getProjectId();
        Long ownerUserId = entity.getOwnerUserId();
        entity.setArchived(true);
        entity.setArchivedAt(Instant.now());
        projectRepository.save(entity);
        for (JobEntity job : jobRepository.findByProjectId(projectId)) {
            JobRecord mem = jobs.get(job.getJobId());
            if (mem != null && (mem.getStatus() == JobRecord.Status.QUEUED
                    || mem.getStatus() == JobRecord.Status.RUNNING
                    || mem.getStatus() == JobRecord.Status.CANCELLING)) {
                forceStopOwnedJob(job.getJobId(), ownerUserId);
            }
            jobs.remove(job.getJobId());
        }
        if (credentialRepository != null) {
            credentialRepository.deleteByProjectId(projectId);
        }
        jobRepository.deleteByProjectId(projectId);
        projectRepository.delete(entity);
        try {
            filesystemStoreFor(projectId).deleteProject(projectId);
            ProjectStore.deleteRecursive(storeRootPath.resolve(projectId));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(PortalStore.class)
                    .warn("Project {} removed from portal DB; store folder cleanup failed: {}",
                            projectId, e.getMessage());
        }
        return true;
    }

    /**
     * Owner-only hard delete of one job row (memory + DB). Does not touch disk —
     * callers remove kind-specific folders separately.
     */
    @Transactional
    public boolean deleteOwnedJobRecord(String jobId, Long ownerUserId) {
        Optional<JobRecord> owned = getOwnedJob(jobId, ownerUserId);
        if (owned.isEmpty()) {
            return false;
        }
        jobs.remove(jobId);
        jobRepository.findByJobId(jobId).ifPresent(jobRepository::delete);
        return true;
    }

    /** Deletes every project (and jobs) owned by this user. */
    @Transactional
    public int deleteAllOwnedProjects(Long ownerUserId) {
        List<ProjectEntity> owned = projectRepository.findByOwnerUserIdOrderByIdDesc(ownerUserId);
        int n = 0;
        for (ProjectEntity p : owned) {
            if (deleteOwnedProject(p.getProjectId(), ownerUserId)) {
                n++;
            }
        }
        return n;
    }

    @Transactional
    public synchronized JobRecord saveJob(JobRecord job) {
        boolean creating = job.getStatus() == JobRecord.Status.QUEUED
                && jobRepository.findByJobId(job.getJobId()).isEmpty();
        String previousStatus = jobRepository.findByJobId(job.getJobId())
                .map(JobEntity::getStatus)
                .orElse("");
        if (creating) {
            if (job.getProjectId() != null && !job.getProjectId().isBlank()) {
                projectRepository.findByProjectId(job.getProjectId()).ifPresent(p -> {
                    if (p.isArchived()) {
                        throw new IllegalStateException("PROJECT_ARCHIVED");
                    }
                });
            }
            if (job.isAccountScoped() || job.getProjectId() == null || job.getProjectId().isBlank()) {
                // Account-scoped: permission/approval already enforced at API; skip project env admit.
                if (job.getProviderAllowlistSnapshot() == null || job.getProviderAllowlistSnapshot().isBlank()) {
                    job.setProviderAllowlistSnapshot(
                            delivery.privacy.ProviderPolicy.fromEnvironment().snapshot());
                }
            } else {
                freezeQueuedInputs(job);
                if (dispatchPolicy != null && dispatchPolicy.remote(job.getJobKind())) {
                    job.setRequirePrivateRunner(true); // B08: the engine claims it over Runner API v2
                }
                delivery.job.JobAdmission.require(
                        countActiveJobsForTenant(job.getTenantId()),
                        countRunningJobs(),
                        delivery.job.JobAdmission.Limits.fromEnvironment());
                requireEnvironmentAdmission(job);
                reserveBudget(job);
            }
            // Approval gate then Keel Credits debit (idempotent debit:job:{jobId}). No BudgetLedger change.
            if (accountApprovals != null && job.getOwnerUserId() != null
                    && delivery.portal.service.credits.CreditsBillingKind.fromJobKind(job.getJobKind()) != null) {
                accountApprovals.requireApprovedForJobs(job.getOwnerUserId());
            }
            if (creditService != null) {
                creditService.tryDebitForJob(job);
            }
        }
        JobEntity entity = jobRepository.findByJobId(job.getJobId()).orElseGet(JobEntity::new);
        entity.setJobId(job.getJobId());
        entity.setProjectId(job.getProjectId());
        entity.setAccountScoped(job.isAccountScoped());
        entity.setOwnerUserId(job.getOwnerUserId());
        if (job.getTenantId() != null && !job.getTenantId().isBlank()) {
            entity.setTenantId(job.getTenantId());
        } else if (job.getProjectId() != null && !job.getProjectId().isBlank()) {
            projectRepository.findByProjectId(job.getProjectId())
                    .map(ProjectEntity::getTenantId)
                    .filter(t -> t != null && !t.isBlank())
                    .ifPresent(entity::setTenantId);
        }
        entity.setMode(job.getMode());
        entity.setJobKind(job.getJobKind().name());
        entity.setStatus(job.getStatus().name());
        entity.setPassedCount(job.getPassedCount());
        entity.setTodoCount(job.getTodoCount());
        entity.setProgressCurrent(job.getProgressCurrent());
        entity.setProgressTotal(job.getProgressTotal());
        entity.setMessage(job.getMessage());
        entity.setError(truncate(job.getError(), 1024));
        if (entity.getCreatedAt() == null) {
            Instant created = job.getCreatedAt() != null ? job.getCreatedAt() : Instant.now();
            entity.setCreatedAt(created);
        }
        if (job.getStatus() == JobRecord.Status.COMPLETED
                || job.getStatus() == JobRecord.Status.COMPLETED_WITH_BLOCK
                || job.getStatus() == JobRecord.Status.FAILED
                || job.getStatus() == JobRecord.Status.CANCELLED) {
            if (entity.getCompletedAt() == null) {
                Instant completed = job.getCompletedAt() != null ? job.getCompletedAt() : Instant.now();
                entity.setCompletedAt(completed);
            }
        }
        if (job.getZipPath() != null) {
            entity.setZipPath(job.getZipPath().toAbsolutePath().toString());
        }
        entity.setBaseUrl(job.getBaseUrl() == null ? "" : job.getBaseUrl());
        if (job.getExcelPath() != null) {
            entity.setExcelPath(job.getExcelPath().toAbsolutePath().toString());
        }
        entity.setUsernameCipher(JobSecretCrypto.encrypt(
                job.getUsername() == null ? "" : job.getUsername()));
        entity.setPasswordCipher(JobSecretCrypto.encrypt(
                job.getPassword() == null ? "" : job.getPassword()));
        entity.setGenerateModel(job.getGenerateModel());
        entity.setAttemptId(job.getAttemptId());
        entity.setWorkerId(job.getWorkerId());
        entity.setLeaseUntil(job.getLeaseUntil());
        entity.setCancelGeneration(job.getCancelGeneration());
        entity.setClaimStage(job.getClaimStage());
        if ((job.getProviderAllowlistSnapshot() == null || job.getProviderAllowlistSnapshot().isBlank())
                && job.getStatus() == JobRecord.Status.QUEUED) {
            job.setProviderAllowlistSnapshot(
                    delivery.privacy.ProviderPolicy.fromEnvironment().snapshot());
        }
        entity.setProviderAllowlistSnapshot(job.getProviderAllowlistSnapshot());
        entity.setLibraryRevisionId(job.getLibraryRevisionId());
        entity.setEnvironmentRevisionId(job.getEnvironmentRevisionId());
        entity.setParentJobId(job.getParentJobId());
        entity.setPrecisionMaxSnapshot(job.getPrecisionMaxSnapshot());
        entity.setPrecisionEnabledSnapshot(job.getPrecisionEnabledSnapshot());
        entity.setPrecisionReuseLocatorMemorySnapshot(job.getPrecisionReuseLocatorMemorySnapshot());
        entity.setAuthoringEngineSnapshot(job.getAuthoringEngine().wireValue());
        entity.setRequirePrivateRunner(job.isRequirePrivateRunner());
        entity.setFencingToken(job.getFencingToken());
        if (JobRecord.isTerminal(job.getStatus())
                && (job.getProvidersUsed() == null || job.getProvidersUsed().isBlank())) {
            try {
                delivery.portal.jobs.ProviderUsage.apply(job, recordedDrafts(job));
            } catch (Exception ignored) {
                delivery.portal.jobs.ProviderUsage.apply(job, java.util.List.of());
            }
        }
        entity.setProvidersUsed(job.getProvidersUsed());
        entity.setFallbackUsed(job.isFallbackUsed());
        entity.setFallbackReason(job.getFallbackReason());
        if ((job.getInputSnapshotHash() == null || job.getInputSnapshotHash().isBlank())
                && job.getStatus() == JobRecord.Status.QUEUED) {
            job.setInputSnapshotHash(delivery.portal.jobs.DurableJobClaim.hashInputs(job));
        }
        entity.setInputSnapshotHash(job.getInputSnapshotHash());
        jobRepository.save(entity);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { jobs.put(job.getJobId(), job); }
                    });
        } else jobs.put(job.getJobId(), job);
        if (entity.getTenantId() != null && !entity.getTenantId().isBlank()) {
            job.setTenantId(entity.getTenantId());
        }
        job.setCreatedAt(entity.getCreatedAt());
        job.setCompletedAt(entity.getCompletedAt());
        if (JobRecord.isTerminal(job.getStatus())
                && !job.getStatus().name().equals(previousStatus)
                && webhooks != null) {
            WebhookDispatcher dispatcher = webhooks.getIfAvailable();
            if (dispatcher != null) {
                dispatcher.deliverAsync(job);
            }
        }
        if (attentionAlerts != null
                && AttentionAlertSender.newlyNeedsAttention(previousStatus, job.getStatus().name())) {
            try {
                attentionAlerts.notifyAttention(job, previousStatus);
            } catch (Exception e) {
                // A notification failure must not fail the job save.
            }
        }
        return job;
    }

    public void syncJobPersistence(JobRecord job) {
        saveJob(job);
    }

    public synchronized Optional<delivery.portal.jobs.DurableJobClaim.Lease> beginWork(String jobId) {
        JobRecord job = getJob(jobId).orElse(null);
        if (job != null && job.isRequirePrivateRunner()) {
            return Optional.empty();
        }
        if (job != null && !environmentHasRunningSlot(job)) return Optional.empty();
        if (job != null) {
            boolean archived = projectRepository.findByProjectId(job.getProjectId())
                    .map(ProjectEntity::isArchived)
                    .orElse(false);
            if (archived) {
                return Optional.empty();
            }
        }
        Optional<delivery.portal.jobs.DurableJobClaim.Lease> lease = delivery.portal.jobs.DurableJobClaim.tryClaim(
                job, delivery.portal.jobs.DurableJobClaim.newWorkerId(), Instant.now(),
                delivery.portal.jobs.DurableJobClaim.DEFAULT_LEASE);
        if (lease.isPresent()) {
            syncJobPersistence(job);
            // Every worker kind gets lease renewal (previously only ConversionWorker heartbeated).
            leaseKeeper.track(jobId, lease.get());
        }
        return lease;
    }

    private final delivery.portal.jobs.LeaseKeeper leaseKeeper = new delivery.portal.jobs.LeaseKeeper(
            this::heartbeat,
            java.time.Duration.ofMinutes(Long.getLong("delivery.jobs.max-runtime-minutes", 180L)));

    /** Background lease renewal; exposed for ops diagnostics. */
    public delivery.portal.jobs.LeaseKeeper leaseKeeper() {
        return leaseKeeper;
    }

    @jakarta.annotation.PreDestroy
    void stopLeaseKeeper() {
        leaseKeeper.close();
    }

    public synchronized boolean heartbeat(String jobId, delivery.portal.jobs.DurableJobClaim.Lease lease) {
        JobRecord job = getJob(jobId).orElse(null);
        boolean ok = delivery.portal.jobs.DurableJobClaim.heartbeat(
                job, lease, Instant.now(), delivery.portal.jobs.DurableJobClaim.DEFAULT_LEASE);
        if (ok) {
            syncJobPersistence(job);
        }
        return ok;
    }

    public boolean ownsAttempt(JobRecord job, delivery.portal.jobs.DurableJobClaim.Lease lease) {
        return delivery.portal.jobs.DurableJobClaim.mayPublish(job, lease) && !shouldAbortCompletion(job);
    }

    public synchronized <T> T publishAttempt(String jobId, delivery.portal.jobs.DurableJobClaim.Lease lease,
                                             java.util.concurrent.Callable<T> action) throws Exception {
        JobRecord current = getJob(jobId).orElse(null);
        boolean projectPresent = current != null && projectRepository.findByProjectId(current.getProjectId())
                .map(p -> !p.isArchived()).orElse(false);
        if (!projectPresent || !ownsAttempt(current, lease)) throw new delivery.job.JobCancelledException();
        return action.call();
    }

    public synchronized int reconcileExpiredLeases() {
        Instant now = Instant.now();
        int n = 0;
        // Only RUNNING jobs can hold a lease; let the DB filter so we don't load every
        // finished job every tick (used to fetch the whole table).
        for (JobEntity entity : jobRepository.findByStatus(JobRecord.Status.RUNNING.name())) {
            JobRecord job = jobs.get(entity.getJobId());
            if (job == null) {
                job = hydrate(entity);
            }
            if (delivery.portal.jobs.DurableJobClaim.reconcileExpired(job, now)) {
                syncJobPersistence(job);
                n++;
            }
        }
        return n;
    }

    /** Cooperative cancel flag (memory-only; status CANCELLED is persisted). */
    public synchronized void requestCancel(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        cancelRequested.computeIfAbsent(jobId, ignored -> new java.util.concurrent.atomic.AtomicBoolean())
                .set(true);
        getJob(jobId).ifPresent(job -> {
            delivery.portal.jobs.DurableJobClaim.requestCancel(job);
            if (job.getStatus() == JobRecord.Status.QUEUED) {
                job.setStatus(JobRecord.Status.CANCELLED);
                job.setMessage("Cancelled before claim");
            } else if (job.getStatus() == JobRecord.Status.RUNNING) {
                job.setStatus(JobRecord.Status.CANCELLING);
                job.setMessage("Cancellation requested");
            }
            syncJobPersistence(job);
        });
    }

    /**
     * Owner hard-stop: sets the cooperative cancel flag and marks the job CANCELLED immediately
     * so stuck/dead workers no longer block Delete. Workers must not overwrite CANCELLED.
     *
     * @return empty if missing/not owned; {@code ALREADY_DONE} if not active; {@code OK} if stopped
     */
    public Optional<String> forceStopOwnedJob(String jobId, Long ownerUserId) {
        Optional<JobRecord> owned = getOwnedJob(jobId, ownerUserId);
        if (owned.isEmpty()) {
            return Optional.empty();
        }
        JobRecord job = owned.get();
        if (job.getStatus() != JobRecord.Status.QUEUED
                && job.getStatus() != JobRecord.Status.RUNNING
                && job.getStatus() != JobRecord.Status.CANCELLING) {
            return Optional.of("ALREADY_DONE");
        }
        requestCancel(jobId);
        job.setStatus(JobRecord.Status.CANCELLED);
        job.setMessage("Force-stopped by owner");
        syncJobPersistence(job);
        return Optional.of("OK");
    }

    /** True when a finishing worker must not write COMPLETED/FAILED over a force-stop. */
    public boolean shouldAbortCompletion(JobRecord job) {
        if (job == null) {
            return false;
        }
        return job.getStatus() == JobRecord.Status.CANCELLED
                || job.getStatus() == JobRecord.Status.CANCELLING
                || isCancelRequested(job.getJobId());
    }

    public boolean isCancelRequested(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return false;
        }
        java.util.concurrent.atomic.AtomicBoolean flag = cancelRequested.get(jobId);
        return flag != null && flag.get();
    }

    public void clearCancelRequest(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        cancelRequested.remove(jobId);
    }

    public Optional<JobRecord> getJob(String jobId) {
        JobRecord mem = jobs.get(jobId);
        if (mem != null) {
            return Optional.of(mem);
        }
        return jobRepository.findByJobId(jobId).map(this::hydrate);
    }

    /** Idempotency recovery must inspect committed storage, not a provisional in-memory record. */
    public boolean isJobPersisted(String jobId) {
        return jobRepository.findByJobId(jobId).isPresent();
    }

    public Optional<JobRecord> getOwnedJob(String jobId, Long ownerUserId) {
        return getJob(jobId).filter(j -> canAccessJob(j, ownerUserId));
    }

    private boolean canAccessJob(JobRecord job, Long userId) {
        if (job == null || userId == null) {
            return false;
        }
        String tenantId = job.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = projectRepository.findByProjectId(job.getProjectId())
                    .map(ProjectEntity::getTenantId)
                    .orElse("");
        }
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                return delivery.identity.WorkspaceDirectory.open(storeRootPath)
                        .isMember(delivery.identity.TenantId.parse(tenantId), userId);
            } catch (Exception e) {
                return userId.equals(job.getOwnerUserId());
            }
        }
        return userId.equals(job.getOwnerUserId());
    }

    /** Everyone who can see the project: workspace members, or just the owner for a personal project. */
    public java.util.List<Long> projectMemberIds(String projectId) {
        return projectRepository.findByProjectId(projectId).map(p -> {
            String tenantId = p.getTenantId();
            if (tenantId != null && !tenantId.isBlank()) {
                try {
                    return delivery.identity.WorkspaceDirectory.open(storeRootPath)
                            .listMembers(delivery.identity.TenantId.parse(tenantId)).stream()
                            .map(m -> m.userId())
                            .toList();
                } catch (Exception e) {
                    // same fallback as canAccessProject: unreadable directory → owner only
                }
            }
            return java.util.List.of(p.getOwnerUserId());
        }).orElse(java.util.List.of());
    }

    public boolean canOperate(String projectId, Long userId) {
        return projectRepository.findByProjectId(projectId)
                .filter(p -> canAccessProject(p, userId))
                .map(p -> roleAllowsOperate(p, userId))
                .orElse(false);
    }

    public boolean canAdminister(String projectId, Long userId) {
        return projectRepository.findByProjectId(projectId)
                .filter(p -> canAccessProject(p, userId))
                .map(p -> roleAllowsAdminister(p, userId))
                .orElse(false);
    }

    private boolean roleAllowsOperate(ProjectEntity project, Long userId) {
        String tenantId = project.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                return delivery.identity.WorkspaceDirectory.open(storeRootPath)
                        .canOperate(delivery.identity.TenantId.parse(tenantId), userId);
            } catch (Exception e) {
                return project.getOwnerUserId().equals(userId);
            }
        }
        return project.getOwnerUserId().equals(userId);
    }

    private boolean roleAllowsAdminister(ProjectEntity project, Long userId) {
        String tenantId = project.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                return delivery.identity.WorkspaceDirectory.open(storeRootPath)
                        .canAdminister(delivery.identity.TenantId.parse(tenantId), userId);
            } catch (Exception e) {
                return project.getOwnerUserId().equals(userId);
            }
        }
        return project.getOwnerUserId().equals(userId);
    }

    private delivery.identity.TenantId tenantForProject(String projectId) {
        return projectRepository.findByProjectId(projectId)
                .map(this::resolveTenant)
                .orElse(null);
    }

    public List<JobEntity> listJobEntities(Long ownerUserId) {
        return jobRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
    }

    public Map<String, Integer> jobStatusCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (JobEntity e : jobRepository.findAll()) {
            String status = e.getStatus() == null ? "UNKNOWN" : e.getStatus();
            counts.merge(status, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Distinct Base URL targets for the upload form, with last-used username/password.
     * Newest job wins per domain folder key.
     */
    public List<Map<String, Object>> listSavedTargets(Long ownerUserId) {
        Map<String, Map<String, Object>> byDomain = new java.util.LinkedHashMap<>();
        for (JobEntity e : jobRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId)) {
            String baseUrl = e.getBaseUrl() == null ? "" : e.getBaseUrl().trim();
            if (baseUrl.isBlank()) {
                continue;
            }
            String domain = DomainStorePaths.folderNameFromHostOrUrl(baseUrl);
            if (domain.isBlank()) {
                domain = baseUrl;
            }
            if (byDomain.containsKey(domain)) {
                continue;
            }
            String user = "";
            String pass = "";
            try {
                user = JobSecretCrypto.decrypt(e.getUsernameCipher());
                pass = JobSecretCrypto.decrypt(e.getPasswordCipher());
            } catch (RuntimeException ignored) {
                // leave empty
            }
            String host = DomainStorePaths.extractHost(baseUrl);
            Map<String, Object> row = new HashMap<>();
            row.put("domain", domain);
            row.put("baseUrl", baseUrl);
            row.put("label", host == null || host.isBlank() ? domain : host);
            row.put("username", user == null ? "" : user);
            row.put("password", pass == null ? "" : pass);
            Instant when = e.getCompletedAt() != null ? e.getCompletedAt() : e.getCreatedAt();
            row.put("lastUsedAt", when == null ? "" : when.toString());
            byDomain.put(domain, row);
        }
        return new ArrayList<>(byDomain.values());
    }

    /**
     * Bound download for this job only. Never substitutes another version's ZIP.
     */
    public Optional<Path> resolveZip(JobRecord job) {
        if (job == null) {
            return Optional.empty();
        }
        Path persisted = null;
        Optional<JobEntity> entity = jobRepository.findByJobId(job.getJobId());
        if (entity.isPresent() && entity.get().getZipPath() != null && !entity.get().getZipPath().isBlank()) {
            persisted = Path.of(entity.get().getZipPath());
        }
        return ArtifactResolver.boundFile(job.getZipPath(), persisted);
    }

    public boolean hasStoredFramework(String projectId) {
        return filesystemStoreFor(projectId).hasFramework(projectId);
    }

    /** Any user who can open the project may delete archived {@code versions/vN.zip} files. */
    public boolean canDeleteVersionPackages(String projectId, Long userId) {
        return getOwnedProject(projectId, userId).isPresent();
    }

    public void deleteLiveFramework(String projectId) throws Exception {
        filesystemStoreFor(projectId).deleteLiveFramework(projectId);
    }

    private JobRecord hydrate(JobEntity e) {
        Path excel = (e.getExcelPath() == null || e.getExcelPath().isBlank())
                ? Path.of(".")
                : Path.of(e.getExcelPath());
        String user = "";
        String pass = "";
        try {
            user = JobSecretCrypto.decrypt(e.getUsernameCipher());
            pass = JobSecretCrypto.decrypt(e.getPasswordCipher());
        } catch (RuntimeException ignored) {
            // missing key / corrupt cipher → empty creds (job may fail honestly)
        }
        JobRecord job = new JobRecord(
                e.getJobId(),
                e.getProjectId(),
                e.getOwnerUserId(),
                e.getMode(),
                excel,
                e.getBaseUrl() == null ? "" : e.getBaseUrl(),
                user,
                pass,
                false,
                parseJobKind(e.getJobKind())
        );
        job.setAccountScoped(e.isAccountScoped());
        try {
            job.setStatus(JobRecord.Status.valueOf(e.getStatus()));
        } catch (IllegalArgumentException ex) {
            job.setStatus(JobRecord.Status.FAILED);
        }
        job.setPassedCount(e.getPassedCount());
        job.setTodoCount(e.getTodoCount());
        job.setProgressCurrent(e.getProgressCurrent());
        job.setProgressTotal(e.getProgressTotal());
        if (e.getMessage() != null) {
            job.setMessage(e.getMessage());
        }
        if (e.getError() != null && !e.getError().isBlank()) {
            job.setError(e.getError());
        }
        if (e.getZipPath() != null && !e.getZipPath().isBlank()) {
            job.setZipPath(Path.of(e.getZipPath()));
        }
        job.setGenerateModel(e.getGenerateModel());
        job.setAttemptId(e.getAttemptId());
        job.setWorkerId(e.getWorkerId());
        job.setLeaseUntil(e.getLeaseUntil());
        job.setCancelGeneration(e.getCancelGeneration());
        job.setClaimStage(e.getClaimStage());
        job.setInputSnapshotHash(e.getInputSnapshotHash());
        job.setProviderAllowlistSnapshot(e.getProviderAllowlistSnapshot());
        job.setLibraryRevisionId(e.getLibraryRevisionId());
        job.setEnvironmentRevisionId(e.getEnvironmentRevisionId());
        job.setParentJobId(e.getParentJobId());
        job.setPrecisionMaxSnapshot(e.getPrecisionMaxSnapshot());
        job.setPrecisionEnabledSnapshot(e.getPrecisionEnabledSnapshot());
        job.setPrecisionReuseLocatorMemorySnapshot(e.getPrecisionReuseLocatorMemorySnapshot());
        job.setRequirePrivateRunner(e.isRequirePrivateRunner());
        job.setFencingToken(e.getFencingToken());
        job.setProvidersUsed(e.getProvidersUsed());
        job.setFallbackUsed(e.isFallbackUsed());
        job.setFallbackReason(e.getFallbackReason());
        job.setTenantId(e.getTenantId());
        if (job.getTenantId() == null || job.getTenantId().isBlank()) {
            if (e.getProjectId() != null && !e.getProjectId().isBlank()) {
                projectRepository.findByProjectId(e.getProjectId())
                        .map(ProjectEntity::getTenantId)
                        .ifPresent(job::setTenantId);
            }
        }
        job.setCreatedAt(e.getCreatedAt());
        job.setCompletedAt(e.getCompletedAt());
        job.setAuthoringEngine(e.getAuthoringEngineSnapshot() != null
                ? AuthoringEngine.parse(e.getAuthoringEngineSnapshot())
                : delivery.job.AuthoringJobRequestFiles.read(
                delivery.job.AuthoringJobRequestFiles.requestPath(
                        projectDiskRoot(e.getProjectId()), delivery.portal.engine.InProcessJobContext.wire(parseJobKind(e.getJobKind())), e.getJobId())));
        jobs.put(job.getJobId(), job);
        return job;
    }

    private static JobRecord.JobKind parseJobKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return JobRecord.JobKind.CONVERT;
        }
        try {
            return JobRecord.JobKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return JobRecord.JobKind.CONVERT;
        }
    }

    private ProjectRecord toRecord(ProjectEntity e) {
        ProjectRecord rec = new ProjectRecord(
                e.getProjectId(), e.getName(), e.getOwnerUserId(), e.getLatestVersion());
        rec.setBaseUrl(e.getBaseUrl() == null ? "" : e.getBaseUrl());
        rec.setAuthoringEngine(AuthoringEngine.parse(e.getAuthoringEngine()));
        rec.setPrecisionMaxCallsPerJob(e.getPrecisionMaxCallsPerJob() == null
                ? 0
                : e.getPrecisionMaxCallsPerJob());
        rec.setPrecisionReuseLocatorMemory(Boolean.TRUE.equals(e.getPrecisionReuseLocatorMemory()));
        rec.setTenantId(e.getTenantId() == null ? "" : e.getTenantId());
        rec.setPlatform(ProjectPlatform.parse(e.getPlatform()).name());
        rec.setTestDataEnv(e.getTestDataEnv() == null ? "" : e.getTestDataEnv());
        rec.setArchived(e.isArchived());
        if (e.getArchivedAt() != null) {
            rec.setArchivedAt(e.getArchivedAt().toString());
        }
        Optional<Instant> when = lastActivity(e.getProjectId());
        if (when.isPresent()) {
            rec.setLastModified(when.get().toString());
            rec.setLastModifiedLabel(WHEN.format(when.get()));
        } else {
            rec.setLastModified("");
            rec.setLastModifiedLabel("Never converted");
        }
        return rec;
    }

    private void freezeQueuedInputs(JobRecord job) {
        if (job.getPrecisionEnabledSnapshot() == null) {
            job.setPrecisionEnabledSnapshot(portalProperties.isPrecisionAuthoringEnabled()
                    && job.getAuthoringEngine() == AuthoringEngine.PRECISION);
        }
        if (job.getPrecisionMaxSnapshot() <= 0) {
            job.setPrecisionMaxSnapshot(precisionConfigForProject(job.getProjectId()).maxCallsPerJob());
        }
        if (job.getPrecisionReuseLocatorMemorySnapshot() == null) {
            job.setPrecisionReuseLocatorMemorySnapshot(
                    precisionConfigForProject(job.getProjectId()).reuseLocatorMemory());
        }
        if (job.getLibraryRevisionId() == null || job.getLibraryRevisionId().isBlank()) {
            try {
                Path gen = GeneratedStoreLayout.resolveGeneratedDir(
                        storeRootPath, projectDiskRoot(job.getProjectId()), job.getProjectId());
                new LibraryRevisionStore(gen).head().ifPresent(h -> job.setLibraryRevisionId(h.id()));
            } catch (Exception ignored) {
                // no library yet
            }
        }
        // Reruns (parentJobId set) already carry frozen pins from RerunSupport — do not
        // re-resolve EnvironmentStore (D-QA-03: stub env revision ids in compare tests).
        if (job.getParentJobId().isBlank()
                && job.getEnvironmentRevisionId() != null && !job.getEnvironmentRevisionId().isBlank()) {
            try {
                var profile = environmentProfile(job);
                String user = job.getUsername();
                String password = job.getPassword();
                if (!profile.credentialProfileRef().isBlank()) {
                    if (credentialRepository == null) throw new IllegalStateException("Credential store unavailable");
                    var credentials = new ProjectCredentialService(credentialRepository)
                            .resolveForJob(job.getProjectId(), profile.credentialProfileRef());
                    user = credentials.username();
                    password = credentials.passwordPlain();
                }
                job.applyEnvironmentInputs(profile.origin(), user, password);
                if (!profile.providerAllowlist().isBlank()) job.setProviderAllowlistSnapshot(profile.providerAllowlist());
            } catch (Exception e) {
                throw new delivery.job.JobAdmissionException("ENVIRONMENT_INVALID",
                        "Environment revision or its credential profile could not be resolved");
            }
        }
        if (job.getProviderAllowlistSnapshot() == null || job.getProviderAllowlistSnapshot().isBlank()) {
            job.setProviderAllowlistSnapshot(delivery.privacy.ProviderPolicy.fromEnvironment().snapshot());
        }
        job.setInputSnapshotHash(job.getParentJobId().isBlank()
                ? delivery.portal.jobs.DurableJobClaim.hashInputs(job) : delivery.portal.jobs.RerunSupport.hashPinnedInputs(job));
    }

    public Path environmentRoot(String projectId) {
        return projectDiskRoot(projectId).resolve("environments");
    }

    private delivery.env.EnvironmentProfile environmentProfile(JobRecord job) throws Exception {
        return new delivery.env.EnvironmentStore(environmentRoot(job.getProjectId()))
                .read(job.getEnvironmentRevisionId()).profile();
    }

    private long environmentJobCount(JobRecord job, boolean runningOnly) {
        return jobRepository.findByProjectId(job.getProjectId()).stream()
                .filter(e -> job.getEnvironmentRevisionId().equals(e.getEnvironmentRevisionId()))
                .filter(e -> !job.getJobId().equals(e.getJobId()))
                .filter(e -> runningOnly ? JobRecord.Status.RUNNING.name().equals(e.getStatus()) : isActiveStatus(e.getStatus()))
                .count();
    }

    private void requireEnvironmentAdmission(JobRecord job) {
        if (job.getEnvironmentRevisionId().isBlank()) return;
        try {
            var profile = environmentProfile(job);
            // Zero inherits installation limits; a positive value adds a narrower environment cap.
            if (profile.maxQueued() > 0 && environmentJobCount(job, false) >= profile.maxQueued())
                throw new delivery.job.JobAdmissionException("ENVIRONMENT_BUSY", "Environment active-job cap reached");
        } catch (delivery.job.JobAdmissionException e) { throw e; }
        catch (Exception e) { throw new delivery.job.JobAdmissionException("ENVIRONMENT_INVALID", "Environment unavailable"); }
    }

    private boolean environmentHasRunningSlot(JobRecord job) {
        if (job.getEnvironmentRevisionId().isBlank()) return true;
        try {
            int cap = environmentProfile(job).maxRunning();
            return cap == 0 || environmentJobCount(job, true) < cap;
        } catch (Exception e) { return false; }
    }

    public Path webhookFile(String projectId) {
        return projectDiskRoot(projectId).resolve("notifications").resolve("webhook.json");
    }

    public java.util.List<delivery.ir.TcDraft> recordedDrafts(JobRecord job) {
        if (job == null) {
            return java.util.List.of();
        }
        try {
            return delivery.portal.jobs.RecordedEvidence.drafts(projectDiskRoot(job.getProjectId()), job);
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public java.util.List<JobRecord> attemptFamily(JobRecord job) {
        if (job == null) {
            return java.util.List.of();
        }
        String rootId = job.getJobId();
        JobRecord walk = job;
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        while (walk.getParentJobId() != null && !walk.getParentJobId().isBlank() && seen.add(walk.getJobId())) {
            JobRecord parent = getJob(walk.getParentJobId()).orElse(null);
            if (parent == null) {
                rootId = walk.getParentJobId();
                break;
            }
            walk = parent;
            rootId = parent.getJobId();
        }
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        ids.add(rootId);
        java.util.List<JobEntity> projectJobs = jobRepository.findByProjectId(job.getProjectId());
        boolean grew = true;
        while (grew) {
            grew = false;
            for (JobEntity entity : projectJobs) {
                String parent = entity.getParentJobId();
                if (parent != null && !parent.isBlank() && ids.contains(parent) && ids.add(entity.getJobId())) {
                    grew = true;
                }
            }
        }
        java.util.List<JobRecord> out = new java.util.ArrayList<>();
        for (String id : ids) {
            JobRecord found = getJob(id).orElse(null);
            if (found == null) {
                for (JobEntity entity : projectJobs) {
                    if (id.equals(entity.getJobId())) {
                        found = hydrate(entity);
                        break;
                    }
                }
            }
            if (found != null) {
                out.add(found);
            }
        }
        out.sort(java.util.Comparator.comparing(
                j -> j.getCreatedAt() == null ? java.time.Instant.EPOCH : j.getCreatedAt()));
        return out;
    }

    public delivery.job.BudgetLedger.Snapshot budgetSnapshot(String projectId) throws Exception {
        String tenantId = getProject(projectId).map(ProjectRecord::getTenantId).orElse("");
        if (tenantId == null || tenantId.isBlank()) {
            return new delivery.job.BudgetLedger.Snapshot(0, 0, 0, List.of());
        }
        return new delivery.job.BudgetLedger(
                budgetFile(tenantId), delivery.job.BudgetLedger.Limits.fromEnvironment())
                .snapshot();
    }

    public delivery.job.BudgetLedger.Snapshot updateBudgetLimits(
            String projectId, long hardCapUnits, long warningUnits) throws Exception {
        String tenantId = getProject(projectId).map(ProjectRecord::getTenantId).orElse("");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        new delivery.job.BudgetLedger(
                budgetFile(tenantId), delivery.job.BudgetLedger.Limits.fromEnvironment())
                .updateLimits(hardCapUnits, warningUnits);
        return budgetSnapshot(projectId);
    }

    public delivery.job.FailureClassifier.Classification saveFailureClassification(
            JobRecord job, delivery.job.FailureClassifier.Kind effective) throws Exception {
        String raw = (job.getMessage() == null ? "" : job.getMessage())
                + " " + (job.getError() == null ? "" : job.getError());
        delivery.job.FailureClassifier.Kind suggested = delivery.job.FailureClassifier.suggest(raw);
        return triageStore(job.getTenantId()).put(job.getJobId(), suggested, effective);
    }

    public java.util.Optional<delivery.job.FailureClassifier.Classification> failureClassification(JobRecord job)
            throws Exception {
        if (job == null) {
            return java.util.Optional.empty();
        }
        return triageStore(job.getTenantId()).get(job.getJobId());
    }

    private void reserveBudget(JobRecord job) {
        String tenantId = job.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        try {
            new delivery.job.BudgetLedger(budgetFile(tenantId), delivery.job.BudgetLedger.Limits.fromEnvironment())
                    .reserve(job.getJobId(), 1, delivery.job.BudgetLedger.CostKind.ESTIMATED);
        } catch (delivery.job.BudgetLedger.Rejected e) {
            throw new delivery.job.JobAdmissionException(e.code(), e.getMessage());
        } catch (Exception e) {
            throw new delivery.job.JobAdmissionException("BUDGET_UNAVAILABLE",
                    "Budget ledger could not be read or updated; job was not admitted");
        }
    }

    private Path budgetFile(String tenantId) {
        return delivery.identity.ScopePaths.tenantRoot(
                        storeRootPath, delivery.identity.TenantId.parse(tenantId))
                .resolve("budget.json");
    }

    private delivery.job.FailureTriageStore triageStore(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        Path file = delivery.identity.ScopePaths.tenantRoot(
                        storeRootPath, delivery.identity.TenantId.parse(tenantId))
                .resolve("triage.json");
        return new delivery.job.FailureTriageStore(file);
    }

    private int countActiveJobsForTenant(String tenantId) {
        String tenant = tenantId == null ? "" : tenantId;
        int n = 0;
        for (JobEntity e : jobRepository.findAll()) {
            if (!tenant.isBlank() && tenant.equals(e.getTenantId()) && isActiveStatus(e.getStatus())) {
                n++;
            }
        }
        return n;
    }

    private int countRunningJobs() {
        int n = 0;
        for (JobEntity e : jobRepository.findAll()) {
            if (JobRecord.Status.RUNNING.name().equals(e.getStatus())) {
                n++;
            }
        }
        return n;
    }

    private static boolean isActiveStatus(String status) {
        return JobRecord.Status.QUEUED.name().equals(status)
                || JobRecord.Status.RUNNING.name().equals(status)
                || JobRecord.Status.CANCELLING.name().equals(status);
    }

    public delivery.identity.WorkspaceDirectory directory() {
        return delivery.identity.WorkspaceDirectory.open(storeRootPath);
    }

    public String workspaceRole(String projectId, Long userId) {
        delivery.identity.TenantId tenant = tenantForProject(projectId);
        if (tenant == null || userId == null) {
            return "";
        }
        delivery.identity.WorkspaceRole role = directory().role(tenant, userId);
        return role == null ? "" : role.name();
    }

    public int cancelActiveJobsForUserOnTenant(String tenantId, long userId) {
        if (tenantId == null || tenantId.isBlank() || userId <= 0) {
            return 0;
        }
        int n = 0;
        for (JobEntity entity : jobRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId)) {
            String jobTenant = entity.getTenantId();
            if (jobTenant == null || jobTenant.isBlank()) {
                jobTenant = projectRepository.findByProjectId(entity.getProjectId())
                        .map(ProjectEntity::getTenantId)
                        .orElse("");
            }
            if (!tenantId.equals(jobTenant) || !isActiveStatus(entity.getStatus())) {
                continue;
            }
            requestCancel(entity.getJobId());
            getJob(entity.getJobId()).ifPresent(job -> {
                job.setMessage("Cancelled because workspace access was revoked");
                syncJobPersistence(job);
            });
            n++;
        }
        return n;
    }

    @Transactional
    public int removeWorkspaceMember(String projectId, long actorUserId, long targetUserId) throws Exception {
        delivery.identity.TenantId tenant = requireTenant(projectId);
        directory().removeMember(tenant, targetUserId, actorUserId);
        return cancelActiveJobsForUserOnTenant(tenant.value(), targetUserId);
    }

    @Transactional
    public void addWorkspaceMember(
            String projectId, long actorUserId, long targetUserId, delivery.identity.WorkspaceRole role
    ) throws Exception {
        directory().addMember(requireTenant(projectId), targetUserId, role, actorUserId);
    }

    @Transactional
    public void inviteWorkspaceEmail(
            String projectId, long actorUserId, String email, delivery.identity.WorkspaceRole role
    ) throws Exception {
        directory().inviteEmail(requireTenant(projectId), actorUserId, email, role);
    }

    @Transactional
    public void transferWorkspaceOwnership(String projectId, long actorUserId, long toUserId) throws Exception {
        delivery.identity.TenantId tenant = requireTenant(projectId);
        directory().transferOwnership(tenant, actorUserId, toUserId);
        for (ProjectEntity project : projectRepository.findByTenantId(tenant.value())) {
            project.setOwnerUserId(toUserId);
            projectRepository.save(project);
        }
    }

    @Transactional
    public delivery.identity.WorkspaceDirectory.CreatedService createWorkspaceService(
            String projectId, long actorUserId, delivery.identity.WorkspaceRole role, String label
    ) throws Exception {
        return directory().createServiceIdentity(requireTenant(projectId), actorUserId, role, label);
    }

    @Transactional
    public void revokeWorkspaceService(String projectId, long actorUserId, String serviceId) throws Exception {
        directory().revokeServiceIdentity(requireTenant(projectId), serviceId, actorUserId);
    }

    public int consumePendingWorkspaceInvites(String email, long userId) throws Exception {
        return directory().consumePendingInvites(email, userId);
    }

    public delivery.identity.WorkspaceDirectory.CreatedRunner enrollWorkspaceRunner(
            String projectId, long actorUserId, String label
    ) throws Exception {
        return directory().enrollRunner(requireTenant(projectId), actorUserId, label);
    }

    public java.util.List<delivery.identity.WorkspaceDirectory.RunnerEnrollment> listWorkspaceRunners(
            String projectId
    ) throws Exception {
        return directory().listRunners(requireTenant(projectId));
    }

    public void revokeWorkspaceRunner(String projectId, long actorUserId, String runnerId) throws Exception {
        directory().revokeRunner(requireTenant(projectId), runnerId, actorUserId);
    }

    public synchronized Optional<delivery.portal.jobs.DurableJobClaim.Lease> claimNextForRunner(
            delivery.identity.WorkspaceDirectory.RunnerEnrollment runner
    ) throws Exception {
        return claimNextForRunner(runner, null);
    }

    /** B07: same as {@link #claimNextForRunner(delivery.identity.WorkspaceDirectory.RunnerEnrollment)}, limited to {@code kinds} (null = any). */
    public synchronized Optional<delivery.portal.jobs.DurableJobClaim.Lease> claimNextForRunner(
            delivery.identity.WorkspaceDirectory.RunnerEnrollment runner,
            java.util.Set<JobRecord.JobKind> kinds
    ) throws Exception {
        if (runner == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        directory().touchRunnerHeartbeat(runner.id(), runner.tenant(), now);
        var live = directory().runner(runner.tenant(), runner.id()).orElse(runner);
        var rules = delivery.portal.jobs.PrivateRunnerBindings.toRules(live);
        for (JobEntity entity : jobRepository.findAll()) {
            JobRecord job = getJob(entity.getJobId()).orElse(null);
            if (job == null || !job.isRequirePrivateRunner()) {
                continue;
            }
            if (kinds != null && !kinds.isEmpty() && !kinds.contains(job.getJobKind())) {
                continue;
            }
            if (!delivery.portal.jobs.PrivateRunnerBindings.sameTenant(live, job)) {
                continue;
            }
            if (!environmentHasRunningSlot(job)) continue;
            try {
                delivery.portal.jobs.PrivateRunnerRules.claim(rules, job, now);
                syncJobPersistence(job);
                return Optional.of(delivery.portal.jobs.DurableJobClaim.toLease(job));
            } catch (SecurityException | IllegalStateException ignored) {
                // try the next queued private job for this tenant
            }
        }
        return Optional.empty();
    }

    public synchronized boolean heartbeatRunnerLease(
            delivery.identity.WorkspaceDirectory.RunnerEnrollment runner, String jobId, String attemptId, int generation
    ) throws Exception {
        JobRecord job = getJob(jobId).orElse(null);
        if (job == null || !delivery.portal.jobs.PrivateRunnerBindings.sameTenant(runner, job)
                || !runner.id().equals(job.getWorkerId()) || !job.getAttemptId().equals(attemptId)) {
            return false;
        }
        directory().touchRunnerHeartbeat(runner.id(), runner.tenant(), Instant.now());
        var lease = new delivery.portal.jobs.DurableJobClaim.Lease(jobId, attemptId, runner.id(),
                job.getLeaseUntil(), generation, delivery.portal.jobs.DurableJobClaim.Stage.ADMITTED);
        return heartbeat(jobId, lease);
    }

    public synchronized String markRunnerStage(
            delivery.identity.WorkspaceDirectory.RunnerEnrollment runner,
            String jobId,
            String stageName, String attemptId
    ) throws Exception {
        JobRecord job = getJob(jobId).orElse(null);
        if (job == null || !delivery.portal.jobs.PrivateRunnerBindings.sameTenant(runner, job)) {
            throw new IllegalArgumentException("unknown job");
        }
        requireRunnerAttempt(runner, job, attemptId);
        delivery.portal.jobs.DurableJobClaim.Stage stage =
                delivery.portal.jobs.DurableJobClaim.Stage.valueOf(stageName.trim().toUpperCase());
        if (stage != delivery.portal.jobs.DurableJobClaim.Stage.BROWSER
                && stage != delivery.portal.jobs.DurableJobClaim.Stage.EMIT) {
            throw new IllegalArgumentException("unsupported stage");
        }
        delivery.portal.jobs.DurableJobClaim.markStage(job, job.getAttemptId(), stage);
        syncJobPersistence(job);
        directory().touchRunnerHeartbeat(runner.id(), runner.tenant(), Instant.now());
        return job.getClaimStage();
    }

    public synchronized void completeFromRunner(
            delivery.identity.WorkspaceDirectory.RunnerEnrollment runner,
            String jobId,
            JobRecord.Status status,
            int passed,
            int todo,
            String message,
            String inputSnapshotHash, String attemptId
    ) throws Exception {
        JobRecord job = getJob(jobId).orElse(null);
        if (job == null || !delivery.portal.jobs.PrivateRunnerBindings.sameTenant(runner, job)) {
            throw new IllegalArgumentException("unknown job");
        }
        requireRunnerAttempt(runner, job, attemptId);
        if (inputSnapshotHash != null && !inputSnapshotHash.isBlank()
                && !inputSnapshotHash.equals(job.getInputSnapshotHash())) {
            throw new IllegalStateException("FROZEN_INPUT_MISMATCH");
        }
        if (shouldAbortCompletion(job) || status == JobRecord.Status.CANCELLED
                || status == JobRecord.Status.CANCELLING) {
            job.setStatus(JobRecord.Status.CANCELLED);
            job.setMessage(message == null || message.isBlank() ? "Cancelled" : message);
            syncJobPersistence(job);
            reconcileRunnerBudget(job);
            return;
        }
        job.setPassedCount(passed);
        job.setTodoCount(todo);
        job.setMessage(message);
        job.setStatus(status == null ? JobRecord.Status.COMPLETED : status);
        syncJobPersistence(job);
        reconcileRunnerBudget(job);
    }

    private void reconcileRunnerBudget(JobRecord job) {
        try {
            new delivery.job.BudgetLedger(
                    budgetFile(job.getTenantId()), delivery.job.BudgetLedger.Limits.fromEnvironment())
                    .reconcile(job.getJobId(), 1);
        } catch (Exception ignored) {
            // reservation stays until a later reconcile
        }
    }

    public synchronized Path storeRunnerArtifact(
            delivery.identity.WorkspaceDirectory.RunnerEnrollment runner,
            String jobId,
            byte[] zipBytes, String attemptId
    ) throws Exception {
        JobRecord job = getJob(jobId).orElse(null);
        if (job == null || !delivery.portal.jobs.PrivateRunnerBindings.sameTenant(runner, job)) {
            throw new IllegalArgumentException("unknown job");
        }
        requireRunnerAttempt(runner, job, attemptId);
        Path dest = projectDiskRoot(job.getProjectId()).resolve("runner-artifacts")
                .resolve(jobId + ".zip");
        Files.createDirectories(dest.getParent());
        Files.write(dest, zipBytes == null ? new byte[0] : zipBytes);
        job.setZipPath(dest);
        syncJobPersistence(job);
        return dest;
    }

    public void requireRunnerAttempt(delivery.identity.WorkspaceDirectory.RunnerEnrollment runner,
                                     JobRecord job, String attemptId) {
        if (job == null || runner == null || !delivery.portal.jobs.PrivateRunnerBindings.sameTenant(runner, job)
                || !runner.id().equals(job.getWorkerId()) || attemptId == null || attemptId.isBlank()
                || !attemptId.equals(job.getAttemptId())) throw new SecurityException("runner does not own this attempt");
        if (delivery.portal.jobs.DurableJobClaim.leaseExpired(job, Instant.now())
                || JobRecord.isTerminal(job.getStatus())) throw new IllegalStateException("LEASE_LOST");
    }

    private delivery.identity.TenantId requireTenant(String projectId) {
        delivery.identity.TenantId tenant = tenantForProject(projectId);
        if (tenant == null) {
            throw new IllegalArgumentException("unknown project");
        }
        return tenant;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
