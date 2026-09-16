package delivery.identity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tenant-scoped filesystem layout for stores and job work directories.
 * Legacy domain-shared paths remain in {@link delivery.store.DomainStorePaths}
 * for unscoped / pre-migration reads.
 */
public final class ScopePaths {
    private static final Pattern JOB_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{1,80}$");

    private ScopePaths() {
    }

    public static Path tenantRoot(Path storeRoot, TenantId tenant) {
        requireStore(storeRoot);
        return storeRoot.resolve("tenants").resolve(tenant.value());
    }

    public static Path projectRoot(Path storeRoot, TenantId tenant, String projectId) {
        String id = requireProjectId(projectId);
        return tenantRoot(storeRoot, tenant).resolve("projects").resolve(id);
    }

    public static Path siteRoot(Path storeRoot, TenantId tenant, String hostOrUrl) {
        String folder = delivery.store.DomainStorePaths.folderNameFromHostOrUrl(hostOrUrl);
        if (folder == null || folder.isBlank()) {
            folder = "site";
        }
        return tenantRoot(storeRoot, tenant).resolve("sites").resolve(folder);
    }

    public static Path jobWorkDir(Path workRoot, TenantId tenant, String jobId) {
        requireStore(workRoot);
        return tenantRoot(workRoot, tenant).resolve("jobs").resolve(requireJobId(jobId));
    }

    /**
     * Creates {@code workRoot/tenants/{tenant}/jobs/{jobId}} exclusively.
     * Parent directories may already exist; the job leaf must not.
     */
    public static Path createJobWorkDir(Path workRoot, TenantId tenant, String jobId) throws java.io.IOException {
        Path dir = jobWorkDir(workRoot, tenant, jobId);
        Files.createDirectories(dir.getParent());
        Files.createDirectory(dir);
        return dir;
    }

    private static void requireStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root is required");
        }
    }

    private static String requireProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("project id is required");
        }
        String id = projectId.trim();
        if (id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("invalid project id: " + projectId);
        }
        return id;
    }

    private static String requireJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("job id is required");
        }
        String id = jobId.trim();
        if (id.contains("..") || id.contains("/") || id.contains("\\")
                || !JOB_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid job id: " + jobId);
        }
        return id;
    }

    public static String folderName(TenantId tenant) {
        return tenant.value().toLowerCase(Locale.ROOT);
    }
}
