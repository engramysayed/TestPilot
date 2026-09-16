package delivery.job;

import delivery.identity.ScopePaths;
import delivery.util.ProjectNaming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Resolves a job work directory. Hosted scope never falls back to host+timestamp folders.
 */
public final class JobWorkLayout {
    public static final String TENANT_REQUIRED = "TENANT_REQUIRED";

    private JobWorkLayout() {
    }

    public static Path create(ConversionJobRequest request) throws java.io.IOException {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        TenantScope scope = request.tenantScope() == null
                ? TenantScope.LEGACY_EXPLICIT
                : request.tenantScope();
        if (scope == TenantScope.HOSTED) {
            if (request.tenantId() == null
                    || request.jobId() == null
                    || request.jobId().isBlank()) {
                throw new IllegalStateException(TENANT_REQUIRED);
            }
            return ScopePaths.createJobWorkDir(request.workDir(), request.tenantId(), request.jobId());
        }
        if (request.tenantId() != null && request.jobId() != null && !request.jobId().isBlank()) {
            return ScopePaths.createJobWorkDir(request.workDir(), request.tenantId(), request.jobId());
        }
        String folder = ProjectNaming.fromBaseUrl(request.baseUrl(), Instant.now());
        Path work = request.workDir().resolve(folder);
        Files.createDirectories(work);
        return work;
    }
}
