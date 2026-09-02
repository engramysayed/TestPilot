package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.model.PatchProjectRequest;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.JobSecretCrypto;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import delivery.store.DomainStorePaths;
import delivery.store.ProjectStore;
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
    private final Path storeRootPath;
    private final ProjectStore projectStore;
    private final ProjectRepository projectRepository;
    private final JobRepository jobRepository;

    public PortalStore(DeliveryPortalProperties props,
                       ProjectRepository projectRepository,
                       JobRepository jobRepository) {
        this.storeRootPath = java.nio.file.Path.of(props.getStoreRoot());
        this.projectStore = new ProjectStore(storeRootPath);
        this.projectRepository = projectRepository;
        this.jobRepository = jobRepository;
    }

    /** Project disk root using entity base URL, falling back to latest job hint. */
    public Path projectDiskRoot(String projectId) {
        String hint = resolveBaseUrlHint(projectId);
        return DomainStorePaths.resolveProjectRoot(storeRootPath, hint, projectId);
    }

    public ProjectStore filesystemStore() {
        return projectStore;
    }

    /** Store helper scoped to a project's known domain (entity base URL or latest job URL). */
    public ProjectStore filesystemStoreFor(String projectId) {
        return new ProjectStore(storeRootPath, resolveBaseUrlHint(projectId));
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
        projectRepository.save(entity);
        return toRecord(entity);
    }

    public Optional<ProjectRecord> getProject(String projectId) {
        return projectRepository.findByProjectId(projectId).map(this::toRecord);
    }

    public Optional<ProjectRecord> getOwnedProject(String projectId, Long ownerUserId) {
        return projectRepository.findByProjectId(projectId)
                .filter(p -> p.getOwnerUserId().equals(ownerUserId))
                .map(this::toRecord);
    }

    public List<ProjectRecord> listProjects(Long ownerUserId) {
        return listProjects(ownerUserId, false);
    }

    public List<ProjectRecord> listProjects(Long ownerUserId, boolean includeArchived) {
        List<ProjectEntity> entities = includeArchived
                ? projectRepository.findByOwnerUserIdOrderByIdDesc(ownerUserId)
                : projectRepository.findByOwnerUserIdAndArchivedOrderByIdDesc(ownerUserId, false);
        return entities.stream()
                .map(this::toRecord)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<ProjectRecord> updateOwnedProject(String projectId, Long ownerUserId, PatchProjectRequest patch) {
        Optional<ProjectEntity> owned = projectRepository.findByProjectId(projectId)
                .filter(p -> p.getOwnerUserId().equals(ownerUserId));
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
     * Owner-only delete: jobs for the project, DB project row, and on-disk store folder.
     * Disk cleanup is best-effort so a missing store folder cannot resurrect the DB row.
     */
    @Transactional
    public boolean deleteOwnedProject(String projectId, Long ownerUserId) {
        Optional<ProjectEntity> owned = projectRepository.findByProjectId(projectId)
                .filter(p -> p.getOwnerUserId().equals(ownerUserId));
        if (owned.isEmpty()) {
            return false;
        }
        for (JobEntity job : jobRepository.findByProjectId(projectId)) {
            jobs.remove(job.getJobId());
        }
        jobRepository.deleteByProjectId(projectId);
        projectRepository.delete(owned.get());
        try {
            filesystemStoreFor(projectId).deleteProject(projectId);
            // Also try flat legacy path if nested was empty
            ProjectStore.deleteRecursive(storeRootPath.resolve(projectId));
        } catch (Exception e) {
            // DB already removed — do not roll back; dashboard must go to zero.
            org.slf4j.LoggerFactory.getLogger(PortalStore.class)
                    .warn("Project {} removed from portal DB; store folder cleanup failed: {}",
                            projectId, e.getMessage());
        }
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
        jobs.put(job.getJobId(), job);
        JobEntity entity = jobRepository.findByJobId(job.getJobId()).orElseGet(JobEntity::new);
        entity.setJobId(job.getJobId());
        entity.setProjectId(job.getProjectId());
        entity.setOwnerUserId(job.getOwnerUserId());
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
        jobRepository.save(entity);
        job.setCreatedAt(entity.getCreatedAt());
        job.setCompletedAt(entity.getCompletedAt());
        return job;
    }

    public void syncJobPersistence(JobRecord job) {
        saveJob(job);
    }

    /** Cooperative cancel flag (memory-only; status CANCELLED is persisted). */
    public void requestCancel(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        cancelRequested.computeIfAbsent(jobId, ignored -> new java.util.concurrent.atomic.AtomicBoolean())
                .set(true);
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
        return getJob(jobId).filter(j -> j.getOwnerUserId().equals(ownerUserId));
    }

    public List<JobEntity> listJobEntities(Long ownerUserId) {
        return jobRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
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
     * Resolve a downloadable ZIP for a completed job: in-memory path, persisted path, or latest project version.
     */
    public Optional<Path> resolveZip(JobRecord job) {
        if (job.getZipPath() != null && Files.isRegularFile(job.getZipPath())) {
            return Optional.of(job.getZipPath());
        }
        Optional<JobEntity> entity = jobRepository.findByJobId(job.getJobId());
        if (entity.isPresent() && entity.get().getZipPath() != null) {
            Path p = Path.of(entity.get().getZipPath());
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return filesystemStoreFor(job.getProjectId()).latestVersionZip(job.getProjectId());
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
        job.setCreatedAt(e.getCreatedAt());
        job.setCompletedAt(e.getCompletedAt());
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
}
