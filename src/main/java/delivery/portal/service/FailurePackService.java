package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.portal.model.JobRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FailurePackService {

    public static final int MANIFEST_VERSION = 1;
    public static final String PACK_DIR = "failure-pack";
    public static final String PACK_ZIP = "failure-pack.zip";
    public static final String MANIFEST_NAME = "manifest.json";

    private final PortalStore store;
    private final FailureEvidenceService evidence;
    private final JobVisionSummaryService visionSummary;
    private final ObjectMapper mapper;

    public FailurePackService(
            PortalStore store,
            FailureEvidenceService evidence,
            JobVisionSummaryService visionSummary,
            ObjectMapper mapper
    ) {
        this.store = store;
        this.evidence = evidence;
        this.visionSummary = visionSummary;
        this.mapper = mapper;
    }

    public enum Eligibility { READY, NOT_ELIGIBLE, NOT_FOUND }

    public record PackPaths(Path zip, Path manifest, Map<String, Object> manifestJson) {
    }

    public Eligibility eligibility(String projectId, String jobId, Long ownerId) {
        Optional<JobRecord> job = store.getOwnedJob(jobId, ownerId);
        if (job.isEmpty() || !projectId.equals(job.get().getProjectId())) {
            return Eligibility.NOT_FOUND;
        }
        JobRecord.Status s = job.get().getStatus();
        if (s != JobRecord.Status.FAILED && s != JobRecord.Status.COMPLETED_WITH_BLOCK) {
            return Eligibility.NOT_ELIGIBLE;
        }
        return Eligibility.READY;
    }

    public PackPaths buildOrReuse(String projectId, String jobId, Long ownerId) throws IOException {
        if (eligibility(projectId, jobId, ownerId) != Eligibility.READY) {
            throw new NotEligibleException("Job is not in a failed terminal state");
        }
        Path jobRoot = store.projectDiskRoot(projectId).resolve("execute-runs").resolve(jobId.trim());
        Files.createDirectories(jobRoot);
        Path packRoot = jobRoot.resolve(PACK_DIR);
        Files.createDirectories(packRoot);
        Path zip = packRoot.resolve(PACK_ZIP);
        Path manifestPath = packRoot.resolve(MANIFEST_NAME);
        JobRecord job = store.getOwnedJob(jobId, ownerId).orElseThrow();
        if (Files.isRegularFile(zip) && Files.isRegularFile(manifestPath)
                && Files.getLastModifiedTime(zip).toMillis() >= jobCompletedMs(job)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = mapper.readValue(manifestPath.toFile(), Map.class);
            return new PackPaths(zip, manifestPath, existing);
        }
        Map<String, Object> manifest = buildManifest(projectId, jobId, job);
        Path tmpZip = packRoot.resolve(PACK_ZIP + ".tmp");
        writeZip(projectId, jobId, jobRoot, manifest, tmpZip);
        atomicReplace(tmpZip, zip);
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        return new PackPaths(zip, manifestPath, manifest);
    }

    private long jobCompletedMs(JobRecord job) {
        Instant t = job.getCompletedAt();
        return t == null ? 0L : t.toEpochMilli();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildManifest(String projectId, String jobId, JobRecord job) throws IOException {
        List<Map<String, Object>> failures = new ArrayList<>(evidence.list(projectId, jobId));
        failures.sort(Comparator.comparingInt(r -> toInt(r.get("stepIndex"))));
        Map<String, Object> primary = failures.isEmpty() ? null : failures.get(0);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", MANIFEST_VERSION);
        manifest.put("jobId", jobId);
        manifest.put("projectId", projectId);
        manifest.put("status", job.getStatus().name());
        Instant finished = job.getCompletedAt() == null ? Instant.now() : job.getCompletedAt();
        manifest.put("finishedAt", finished.toString());
        manifest.put("evidenceUrl", "/status?jobId=" + jobId);
        Map<String, Object> vision = visionSummary.summarize(projectId, jobId);
        manifest.put("usedVision", Boolean.TRUE.equals(vision.get("usedVision")));
        manifest.put("vision", Map.of(
                "attemptCount", vision.getOrDefault("attemptCount", 0),
                "successfulFallbackCount", vision.getOrDefault("successfulFallbackCount", 0),
                "domFirst", vision.getOrDefault("domFirst", false)));
        manifest.put("warnings", new ArrayList<String>());
        if (primary == null) {
            manifest.put("failedStep", null);
            warnings(manifest).add("No failure replay folder found for this job");
        } else {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", "step_" + toInt(primary.get("stepIndex")));
            step.put("index", toInt(primary.get("stepIndex")));
            step.put("text", FailurePackSanitizer.text(str(primary.get("reason"))));
            step.put("status", "FAILED");
            manifest.put("failedStep", step);
        }
        List<Map<String, Object>> neighbors = new ArrayList<>();
        manifest.put("neighbors", neighbors);
        List<Map<String, Object>> artifacts = new ArrayList<>();
        Path projectRoot = store.projectDiskRoot(projectId);
        for (Map<String, Object> row : failures) {
            String tcId = str(row.get("tcId"));
            int step = toInt(row.get("stepIndex"));
            Optional<Path> screen = evidence.screen(projectId, jobId, tcId, step);
            if (screen.isEmpty()) {
                artifacts.add(artifactEntry("screenshot", null, "missing", tcId, step, null, 0, false));
                continue;
            }
            Path file = screen.get();
            if (!isUnderJobRoot(file, projectRoot, jobId)) {
                artifacts.add(artifactEntry("screenshot", null, "inaccessible", tcId, step, null, 0, false));
                continue;
            }
            byte[] bytes = Files.readAllBytes(file);
            String hash = sha256(bytes);
            String folder = str(row.get("tcFolder"));
            if (folder.isBlank()) {
                folder = tcId;
            }
            String entry = "evidence/" + folder + "/failure/" + step + "/screen.png";
            artifacts.add(artifactEntry("screenshot", entry, "present", tcId, step, hash, bytes.length, true));
        }
        manifest.put("artifacts", artifacts);
        String packStatus = artifacts.stream().anyMatch(a -> "present".equals(a.get("availability")))
                ? (artifacts.stream().anyMatch(a -> "missing".equals(a.get("availability"))) ? "PARTIAL" : "READY")
                : "PARTIAL";
        manifest.put("packStatus", packStatus);
        return manifest;
    }

    private static Map<String, Object> artifactEntry(
            String kind, String path, String availability, String tcId, int step,
            String sha256, int bytes, boolean redacted
    ) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("kind", kind);
        a.put("path", path);
        a.put("availability", availability);
        a.put("sourceJobId", tcId.isBlank() ? "" : tcId);
        a.put("sourceStepId", "step_" + step);
        if ("present".equals(availability)) {
            a.put("sha256", sha256);
            a.put("bytes", bytes);
            a.put("redacted", redacted);
        }
        return a;
    }

    private void writeZip(
            String projectId,
            String jobId,
            Path jobRoot,
            Map<String, Object> manifest,
            Path tmpZip
    ) throws IOException {
        try (OutputStream fos = Files.newOutputStream(tmpZip,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            byte[] manifestBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            zos.putNextEntry(new ZipEntry(MANIFEST_NAME));
            zos.write(manifestBytes);
            zos.closeEntry();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> artifacts = (List<Map<String, Object>>) manifest.get("artifacts");
            for (Map<String, Object> art : artifacts) {
                if (!"present".equals(art.get("availability"))) {
                    continue;
                }
                String rel = str(art.get("path"));
                String tcId = str(art.get("sourceJobId"));
                int step = toInt(str(art.get("sourceStepId")).replace("step_", ""));
                Optional<Path> src = evidence.screen(projectId, jobId, tcId, step);
                if (src.isEmpty() || !Files.isRegularFile(src.get())) {
                    continue;
                }
                if (!isUnderJobRoot(src.get(), store.projectDiskRoot(projectId), jobId)) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(rel.replace('\\', '/')));
                zos.write(Files.readAllBytes(src.get()));
                zos.closeEntry();
            }
        }
    }

    static boolean isUnderJobRoot(Path file, Path projectRoot, String jobId) {
        Path jobRoot = projectRoot.toAbsolutePath().normalize()
                .resolve("execute-runs").resolve(jobId).normalize();
        Path norm = file.toAbsolutePath().normalize();
        return norm.startsWith(jobRoot);
    }

    private static void atomicReplace(Path tmp, Path dest) throws IOException {
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    @SuppressWarnings("unchecked")
    private static List<String> warnings(Map<String, Object> manifest) {
        Object w = manifest.get("warnings");
        if (w instanceof List<?> list) {
            return (List<String>) list;
        }
        List<String> out = new ArrayList<>();
        manifest.put("warnings", out);
        return out;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            return "";
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static class NotEligibleException extends RuntimeException {
        public NotEligibleException(String message) {
            super(message);
        }
    }
}
