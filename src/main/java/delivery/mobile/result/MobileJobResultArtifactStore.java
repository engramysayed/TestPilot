package delivery.mobile.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import delivery.portal.DeliveryPortalProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Persists published {@link MobileJobResultView} as a job artifact side file (M2-07).
 * Path: {@code {storeRoot}/tenants/{tenant}/projects/{projectId}/jobs/{jobId}/mobile-result.json}.
 * Never rewrites admission input files in the same directory.
 */
@Component
public class MobileJobResultArtifactStore {
    public static final String RESULT_FILE = "mobile-result.json";
    public static final String ADMISSION_FILE = "admission-inputs.json";

    private final DeliveryPortalProperties portalProperties;
    private final ObjectMapper mapper;

    public MobileJobResultArtifactStore(DeliveryPortalProperties portalProperties, ObjectMapper mapper) {
        this.portalProperties = portalProperties;
        this.mapper = mapper;
    }

    public Path jobDir(String tenantId, String projectId, String jobId) {
        Path storeRoot = Path.of(portalProperties.getStoreRoot());
        return ScopePaths.projectRoot(storeRoot, TenantId.parse(tenantId), projectId)
                .resolve("jobs")
                .resolve(requireSafeId(jobId, "jobId"));
    }

    public Path resultPath(String tenantId, String projectId, String jobId) {
        return jobDir(tenantId, projectId, jobId).resolve(RESULT_FILE);
    }

    public Path admissionPath(String tenantId, String projectId, String jobId) {
        return jobDir(tenantId, projectId, jobId).resolve(ADMISSION_FILE);
    }

    /** Write published view; does not create/modify {@link #ADMISSION_FILE}. */
    public void write(String tenantId, String projectId, MobileJobResultView view) throws IOException {
        if (view == null) {
            throw new IllegalArgumentException("view required");
        }
        Path dir = jobDir(tenantId, projectId, view.jobId());
        Files.createDirectories(dir);
        Path target = dir.resolve(RESULT_FILE);
        Path tmp = dir.resolve(RESULT_FILE + ".tmp");
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(sanitize(view));
        Files.write(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public Optional<MobileJobResultView> read(String tenantId, String projectId, String jobId) throws IOException {
        Path path = resultPath(tenantId, projectId, jobId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        MobileJobResultView view = mapper.readValue(Files.readAllBytes(path), MobileJobResultView.class);
        return Optional.of(sanitize(view));
    }

    /** Snapshot admission bytes for immutability checks (tests / callers). Absent → empty. */
    public Optional<byte[]> readAdmissionBytes(String tenantId, String projectId, String jobId) throws IOException {
        Path path = admissionPath(tenantId, projectId, jobId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(path));
    }

    public void writeAdmissionSnapshot(String tenantId, String projectId, String jobId, String json)
            throws IOException {
        Path dir = jobDir(tenantId, projectId, jobId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ADMISSION_FILE), json == null ? "{}" : json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /**
     * Normalize published view: recompute aggregate preference, force containsRawSecret=false.
     */
    static MobileJobResultView sanitize(MobileJobResultView view) {
        if (view == null) {
            return null;
        }
        // Rebuild via of() so aggregate preference matches contract even if runner sent a wrong aggregate.
        return MobileJobResultView.of(
                view.jobId(),
                view.attemptId(),
                view.projectId(),
                view.runtime(),
                view.cases(),
                view.timingMs());
    }

    private static String requireSafeId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(label + " required");
        }
        String t = id.trim();
        if (t.contains("..") || t.contains("/") || t.contains("\\")) {
            throw new IllegalArgumentException("invalid " + label);
        }
        return t;
    }
}
