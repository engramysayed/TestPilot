package delivery.portal.service;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;
import delivery.portal.DeliveryPortalProperties;
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

    public PortalStore(DeliveryPortalProperties props,
                       ProjectRepository projectRepository,
                       JobRepository jobRepository) {
        this(props, projectRepository, jobRepository, null);
    }

    @Autowired
    public PortalStore(DeliveryPortalProperties props,
                       ProjectRepository projectRepository,
                       JobRepository jobRepository,
                       ProjectCredentialRepository credentialRepository) {
        this.portalProperties = props;
        this.storeRootPath = java.nio.file.Path.of(props.getStoreRoot());
        this.projectStore = new ProjectStore(storeRootPath);
        this.projectRepository = projectRepository;
        this.jobRepository = jobRepository;
        this.credentialRepository = credentialRepository;
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
        return new PrecisionJobConfig(enabled, max);
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
        return createProject(name, null, ownerUserId);
    }

    @Transactional
    public ProjectRecord createProject(String name, String baseUrlHint, Long ownerUserId) {
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

    public Optional<ProjectRecord> getProject(String projectId) {
        return projectRepository.findByProjectId(projectId).map(this::toRecord);
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
        if (patch.authoringEngine() != null) {
            entity.setAuthoringEngine(AuthoringEngine.parse(patch.authoringEngine()).wireValue());
        }
        if (patch.precisionMaxCallsPerJob() != null) {
            int max = patch.precisionMaxCallsPerJob();
            entity.setPrecisionMaxCallsPerJob(max <= 0 ? null : Math.min(max, 500));
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
     * Owner-only delete: tombstone, stop workers, then purge credentials, jobs, and disk.
     * Partial disk failure is logged and retryable because the DB row is already gone.
     */
    @Transactional
    public boolean deleteOwnedProject(String projectId, Long ownerUserId) {
        Optional<ProjectEntity> owned = projectRepository.findByProjectId(projectId)
                .filter(p -> p.getOwnerUserId().equals(ownerUserId));
        if (owned.isEmpty()) {
            return false;
        }
        return purgeProject(owned.get());
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

    private boolean purgeProject(ProjectEntity entity) {
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
    public JobRecord saveJob(JobRecord job) {
        boolean creating = job.getStatus() == JobRecord.Status.QUEUED
                && jobRepository.findByJobId(job.getJobId()).isEmpty();
        if (creating) {
            projectRepository.findByProjectId(job.getProjectId()).ifPresent(p -> {
                if (p.isArchived()) {
                    throw new IllegalStateException("PROJECT_ARCHIVED");
                }
            });
            freezeQueuedInputs(job);
            delivery.job.JobAdmission.require(
                    countActiveJobsForTenant(job.getTenantId()),
                    countRunningJobs(),
                    delivery.job.JobAdmission.Limits.fromEnvironment());
        }
        jobs.put(job.getJobId(), job);
        JobEntity entity = jobRepository.findByJobId(job.getJobId()).orElseGet(JobEntity::new);
        entity.setJobId(job.getJobId());
        entity.setProjectId(job.getProjectId());
        entity.setOwnerUserId(job.getOwnerUserId());
        if (job.getTenantId() != null && !job.getTenantId().isBlank()) {
            entity.setTenantId(job.getTenantId());
        } else {
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
        entity.setPrecisionMaxSnapshot(job.getPrecisionMaxSnapshot());
        if ((job.getInputSnapshotHash() == null || job.getInputSnapshotHash().isBlank())
                && job.getStatus() == JobRecord.Status.QUEUED) {
            job.setInputSnapshotHash(delivery.job.DurableJobClaim.hashInputs(job));
        }
        entity.setInputSnapshotHash(job.getInputSnapshotHash());
        jobRepository.save(entity);
        if (entity.getTenantId() != null && !entity.getTenantId().isBlank()) {
            job.setTenantId(entity.getTenantId());
        }
        job.setCreatedAt(entity.getCreatedAt());
        job.setCompletedAt(entity.getCompletedAt());
        return job;
    }

    public void syncJobPersistence(JobRecord job) {
        saveJob(job);
    }

    public synchronized Optional<delivery.job.DurableJobClaim.Lease> beginWork(String jobId) {
        JobRecord job = getJob(jobId).orElse(null);
        if (job != null) {
            boolean archived = projectRepository.findByProjectId(job.getProjectId())
                    .map(ProjectEntity::isArchived)
                    .orElse(false);
            if (archived) {
                return Optional.empty();
            }
        }
        Optional<delivery.job.DurableJobClaim.Lease> lease = delivery.job.DurableJobClaim.tryClaim(
                job, delivery.job.DurableJobClaim.newWorkerId(), Instant.now(),
                delivery.job.DurableJobClaim.DEFAULT_LEASE);
        if (lease.isPresent()) {
            syncJobPersistence(job);
        }
        return lease;
    }

    public synchronized boolean heartbeat(String jobId, delivery.job.DurableJobClaim.Lease lease) {
        JobRecord job = getJob(jobId).orElse(null);
        boolean ok = delivery.job.DurableJobClaim.heartbeat(
                job, lease, Instant.now(), delivery.job.DurableJobClaim.DEFAULT_LEASE);
        if (ok) {
            syncJobPersistence(job);
        }
        return ok;
    }

    public boolean ownsAttempt(JobRecord job, delivery.job.DurableJobClaim.Lease lease) {
        return delivery.job.DurableJobClaim.mayPublish(job, lease) && !shouldAbortCompletion(job);
    }

    public synchronized int reconcileExpiredLeases() {
        Instant now = Instant.now();
        int n = 0;
        for (JobEntity entity : jobRepository.findAll()) {
            JobRecord job = jobs.computeIfAbsent(entity.getJobId(), ignored -> hydrate(entity));
            if (delivery.job.DurableJobClaim.reconcileExpired(job, now)) {
                syncJobPersistence(job);
                n++;
            }
        }
        return n;
    }

    /** Cooperative cancel flag (memory-only; status CANCELLED is persisted). */
    public void requestCancel(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        cancelRequested.computeIfAbsent(jobId, ignored -> new java.util.concurrent.atomic.AtomicBoolean())
                .set(true);
        getJob(jobId).ifPresent(job -> {
            delivery.job.DurableJobClaim.requestCancel(job);
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
        job.setPrecisionMaxSnapshot(e.getPrecisionMaxSnapshot());
        job.setTenantId(e.getTenantId());
        if (job.getTenantId() == null || job.getTenantId().isBlank()) {
            projectRepository.findByProjectId(e.getProjectId())
                    .map(ProjectEntity::getTenantId)
                    .ifPresent(job::setTenantId);
        }
        job.setCreatedAt(e.getCreatedAt());
        job.setCompletedAt(e.getCompletedAt());
        job.setAuthoringEngine(delivery.job.AuthoringJobRequestFiles.read(
                delivery.job.AuthoringJobRequestFiles.requestPath(
                        projectDiskRoot(e.getProjectId()), parseJobKind(e.getJobKind()), e.getJobId())));
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
        rec.setTenantId(e.getTenantId() == null ? "" : e.getTenantId());
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
        if (job.getProviderAllowlistSnapshot() == null || job.getProviderAllowlistSnapshot().isBlank()) {
            job.setProviderAllowlistSnapshot(delivery.privacy.ProviderPolicy.fromEnvironment().snapshot());
        }
        if (job.getPrecisionMaxSnapshot() <= 0) {
            job.setPrecisionMaxSnapshot(precisionConfigForProject(job.getProjectId()).maxCallsPerJob());
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
        if (job.getInputSnapshotHash() == null || job.getInputSnapshotHash().isBlank()) {
            job.setInputSnapshotHash(delivery.job.DurableJobClaim.hashInputs(job));
        }
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
}
