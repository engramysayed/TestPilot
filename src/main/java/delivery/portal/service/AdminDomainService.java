package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectRepository;
import delivery.store.DomainStorePaths;
import delivery.store.ProjectStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class AdminDomainService {
    private static final Logger log = LoggerFactory.getLogger(AdminDomainService.class);
    private final Path storeRoot;
    private final ProjectRepository projects;
    private final JobRepository jobs;

    @Autowired
    public AdminDomainService(DeliveryPortalProperties props,
                              ProjectRepository projects,
                              JobRepository jobs) {
        this(Path.of(props.getStoreRoot()), projects, jobs);
    }

    /** Test / tooling constructor (not used by Spring). */
    AdminDomainService(Path storeRoot) {
        this(storeRoot, null, null);
    }

    AdminDomainService(Path storeRoot, ProjectRepository projects, JobRepository jobs) {
        this.storeRoot = storeRoot.toAbsolutePath().normalize();
        this.projects = projects;
        this.jobs = jobs;
    }

    public List<Map<String, Object>> listDomains() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(storeRoot)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(storeRoot)) {
            List<Path> dirs = stream
                    .filter(Files::isDirectory)
                    .filter(p -> !DomainStorePaths.isStoreRootReserved(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            for (Path dir : dirs) {
                out.add(summarizeDomain(dir));
            }
        }
        return out;
    }

    /** Opt-in only — does not run on list. */
    public List<String> migrateLegacyFlatProjects() throws Exception {
        List<String> moved = DomainStorePaths.migrateLegacyFlatProjects(storeRoot);
        if (!moved.isEmpty()) {
            log.info("Migrated legacy store folders: {}", String.join(", ", moved));
        }
        return moved;
    }

    public Map<String, Object> getDomain(String domainKey) throws Exception {
        Path dir = requireDomainDir(domainKey);
        return summarizeDomain(dir);
    }

    /**
     * Wipes {@code delivery-store/<domain>/} and removes matching portal projects/jobs
     * so Dashboard counters stay in sync.
     */
    @Transactional
    public Map<String, Object> deleteDomain(String domainKey, String confirmName) throws Exception {
        String sanitized = DomainStorePaths.sanitizeFolder(domainKey);
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Domain is required");
        }
        if (DomainStorePaths.isStoreRootReserved(sanitized)) {
            throw new IllegalArgumentException("Cannot delete reserved store path");
        }
        String confirm = DomainStorePaths.sanitizeFolder(confirmName);
        if (!sanitized.equals(confirm)) {
            throw new IllegalArgumentException("Confirmation name does not match domain");
        }
        Path dir = requireDomainDir(sanitized);
        Set<String> projectIds = collectProjectIdsForDomain(sanitized, dir);
        int removedProjects = 0;
        int removedJobs = 0;
        if (projects != null && jobs != null) {
            for (String projectId : projectIds) {
                List<JobEntity> jobRows = jobs.findByProjectId(projectId);
                removedJobs += jobRows.size();
                jobs.deleteByProjectId(projectId);
                if (projects.findByProjectId(projectId).isPresent()) {
                    projects.findByProjectId(projectId).ifPresent(projects::delete);
                    removedProjects++;
                }
            }
        }
        ProjectStore.deleteRecursive(dir);
        log.info("Deleted domain {} — portal projects={}, jobs={}", sanitized, removedProjects, removedJobs);
        Map<String, Object> out = new HashMap<>();
        out.put("domain", sanitized);
        out.put("removedProjects", removedProjects);
        out.put("removedJobs", removedJobs);
        return out;
    }

    private Set<String> collectProjectIdsForDomain(String domainFolder, Path domainDir) throws Exception {
        Set<String> ids = new HashSet<>();
        if (DomainStorePaths.looksLikeProject(domainDir)) {
            ids.add(domainFolder);
        }
        if (Files.isDirectory(domainDir)) {
            try (Stream<Path> nested = Files.list(domainDir)) {
                nested.filter(Files::isDirectory)
                        .filter(DomainStorePaths::looksLikeProject)
                        .forEach(p -> ids.add(p.getFileName().toString()));
            }
        }
        if (jobs != null) {
            for (JobEntity job : jobs.findAll()) {
                String fromUrl = DomainStorePaths.folderNameFromHostOrUrl(job.getBaseUrl());
                if (domainFolder.equals(fromUrl) && job.getProjectId() != null && !job.getProjectId().isBlank()) {
                    ids.add(job.getProjectId());
                }
            }
        }
        return ids;
    }

    private Path requireDomainDir(String domainKey) {
        String sanitized = DomainStorePaths.sanitizeFolder(domainKey);
        if (sanitized.isBlank() || sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\")) {
            throw new IllegalArgumentException("Invalid domain");
        }
        if (DomainStorePaths.isStoreRootReserved(sanitized)) {
            throw new IllegalArgumentException("Invalid domain");
        }
        Path dir = storeRoot.resolve(sanitized).normalize();
        if (!dir.startsWith(storeRoot) || dir.equals(storeRoot)) {
            throw new IllegalArgumentException("Invalid domain path");
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Domain not found");
        }
        return dir;
    }

    private Map<String, Object> summarizeDomain(Path domainDir) throws Exception {
        String key = domainDir.getFileName().toString();
        int projectCount = 0;
        int memoryEntries = 0;
        int locatorMapCount = 0;
        long locatorMapBytes = 0;
        Instant lastMod = Instant.ofEpochMilli(Files.getLastModifiedTime(domainDir).toMillis());

        boolean flatProject = DomainStorePaths.looksLikeProject(domainDir);
        if (flatProject) {
            projectCount = 1;
            memoryEntries += countMemoryEntries(domainDir.resolve("domain-locator-memory.json"));
            MapSize map = locatorMapStats(domainDir.resolve("locator-map.json"));
            locatorMapCount += map.count();
            locatorMapBytes += map.bytes();
            lastMod = maxMod(lastMod, domainDir);
        }

        try (Stream<Path> nested = Files.list(domainDir)) {
            for (Path child : nested.filter(Files::isDirectory).toList()) {
                if (DomainStorePaths.looksLikeProject(child)) {
                    projectCount++;
                    memoryEntries += countMemoryEntries(child.resolve("domain-locator-memory.json"));
                    MapSize map = locatorMapStats(child.resolve("locator-map.json"));
                    locatorMapCount += map.count();
                    locatorMapBytes += map.bytes();
                    lastMod = maxMod(lastMod, child);
                }
            }
        }

        Map<String, Object> row = new HashMap<>();
        row.put("domain", key);
        row.put("projectCount", projectCount);
        row.put("memoryEntries", memoryEntries);
        row.put("locatorMapCount", locatorMapCount);
        row.put("locatorMapBytes", locatorMapBytes);
        row.put("locatorMapPresent", locatorMapCount > 0);
        row.put("lastModified", lastMod.toString());
        row.put("path", domainDir.toString());
        return row;
    }

    private record MapSize(int count, long bytes) {
    }

    private static MapSize locatorMapStats(Path file) {
        if (!Files.isRegularFile(file)) {
            return new MapSize(0, 0);
        }
        try {
            return new MapSize(1, Files.size(file));
        } catch (Exception e) {
            return new MapSize(1, 0);
        }
    }

    private static Instant maxMod(Instant current, Path dir) throws Exception {
        Instant best = current;
        try (Stream<Path> walk = Files.walk(dir, 2)) {
            for (Path p : walk.toList()) {
                Instant t = Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis());
                if (t.isAfter(best)) {
                    best = t;
                }
            }
        }
        return best;
    }

    private static int countMemoryEntries(Path file) {
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        try {
            JSONObject root = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            JSONArray entries = root.optJSONArray("entries");
            return entries == null ? 0 : entries.length();
        } catch (Exception e) {
            return 0;
        }
    }
}
