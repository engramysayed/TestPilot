package delivery.runner;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;
import delivery.identity.TenantId;
import delivery.job.ConversionJobRequest;
import delivery.job.DryRunExecuteService;
import delivery.job.ExecuteJobResult;
import delivery.job.ExecuteJobRunner;
import delivery.job.JobProgressTracker;
import delivery.job.TenantScope;
import delivery.portal.security.ApiRequestHeaderFilter;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Outbound private-runner agent. Talks to the portal; the control plane never dials in.
 */
public final class PrivateRunnerAgent {
    private final String portal;
    private final String token;
    private final Path workDir;
    private final boolean dryRun;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PrivateRunnerAgent(String portal, String token, Path workDir, boolean dryRun) {
        this.portal = portal.endsWith("/") ? portal.substring(0, portal.length() - 1) : portal;
        this.token = token;
        this.workDir = workDir;
        this.dryRun = dryRun;
    }

    public static void main(String[] args) throws Exception {
        String portal = envOrArg(args, "--portal", "KEEL_PORTAL_URL", "http://127.0.0.1:8081");
        String token = envOrArg(args, "--token", "KEEL_RUNNER_TOKEN", "");
        Path work = Path.of(envOrArg(args, "--work-dir", "KEEL_RUNNER_WORK", "./runner-work"));
        boolean dry = Boolean.parseBoolean(envOrArg(args, "--dry-run", "KEEL_RUNNER_DRY_RUN", "true"));
        if (token == null || token.isBlank()) {
            System.err.println("KEEL_RUNNER_TOKEN or --token is required");
            System.exit(2);
        }
        new PrivateRunnerAgent(portal, token, work, dry).runLoop();
    }

    public int runOnce() throws Exception {
        heartbeat();
        HttpResponse<String> claim = post("/api/v1/runners/claim", "");
        if (claim.statusCode() == 204) {
            return 0;
        }
        if (claim.statusCode() != 200) {
            throw new IllegalStateException("claim failed: " + claim.statusCode() + " " + claim.body());
        }
        JSONObject body = new JSONObject(claim.body());
        String jobId = body.getString("jobId");
        executeClaimed(jobId, body.optString("inputSnapshotHash"), body.optString("providerAllowlistSnapshot"));
        return 1;
    }

    public void runLoop() throws Exception {
        Files.createDirectories(workDir);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                runOnce();
            } catch (Exception e) {
                System.err.println("runner cycle: " + e.getMessage());
            }
            Thread.sleep(2000);
        }
    }

    private void executeClaimed(String jobId, String inputHash, String allowlist) throws Exception {
        byte[] pack = getBytes("/api/v1/runners/jobs/" + jobId + "/input");
        Path jobDir = workDir.resolve(jobId);
        Files.createDirectories(jobDir);
        Path excel = jobDir.resolve("suite.xlsx");
        JSONObject meta = unzipInput(pack, excel, jobDir);
        if (cancelRequested(jobId)) {
            return;
        }
        ExecuteJobResult result = runJob(jobId, meta, excel, jobDir);
        byte[] artifacts = zipDir(jobDir);
        String sig = PrivateRunnerArtifacts.signature(token, jobId, meta.optString("attemptId", ""), artifacts);
        HttpRequest upload = authorized(HttpRequest.newBuilder()
                .uri(URI.create(portal + "/api/v1/runners/jobs/" + jobId + "/artifacts"))
                .header(PrivateRunnerArtifacts.SIGNATURE_HEADER, "sha256=" + sig)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(artifacts)));
        http.send(upload, HttpResponse.BodyHandlers.ofString());
        JSONObject complete = new JSONObject();
        complete.put("status", "COMPLETED");
        complete.put("passedCount", result.passed());
        complete.put("todoCount", result.todo());
        complete.put("message", result.message() == null ? "" : result.message());
        complete.put("inputSnapshotHash", inputHash == null ? meta.optString("inputSnapshotHash") : inputHash);
        complete.put("providerAllowlistSnapshot",
                allowlist == null || allowlist.isBlank() ? meta.optString("providerAllowlistSnapshot") : allowlist);
        post("/api/v1/runners/jobs/" + jobId + "/complete", complete.toString());
    }

    private ExecuteJobResult runJob(String jobId, JSONObject meta, Path excel, Path jobDir) throws Exception {
        TenantId tenant = TenantId.parse(meta.getString("tenantId"));
        ConversionJobRequest request = new ConversionJobRequest(
                meta.optString("projectId"),
                excel,
                meta.optString("baseUrl"),
                meta.optString("username"),
                meta.optString("password"),
                jobDir,
                jobDir.resolve("store"),
                jobDir.resolve("templates"),
                meta.optString("mode", "EXECUTE"),
                "",
                "",
                false,
                false,
                AuthoringEngine.KEEL,
                new PrecisionJobConfig(false, 50),
                tenant,
                jobId,
                TenantScope.HOSTED
        );
        if (dryRun) {
            return new DryRunExecuteService().run(request, jobId, new JobProgressTracker(), () -> {
                try {
                    return cancelRequested(jobId);
                } catch (Exception e) {
                    return false;
                }
            });
        }
        return new ExecuteJobRunner().run(request, jobId, () -> {
            try {
                return cancelRequested(jobId);
            } catch (Exception e) {
                return false;
            }
        });
    }

    private boolean cancelRequested(String jobId) throws Exception {
        HttpResponse<String> res = get("/api/v1/runners/jobs/" + jobId);
        if (res.statusCode() != 200) {
            return false;
        }
        return new JSONObject(res.body()).optBoolean("cancelRequested", false);
    }

    private void heartbeat() throws Exception {
        post("/api/v1/runners/heartbeat", "");
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(portal + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json));
        return http.send(authorized(b), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(portal + path)).GET();
        return http.send(authorized(b), HttpResponse.BodyHandlers.ofString());
    }

    private byte[] getBytes(String path) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(portal + path)).GET();
        return http.send(authorized(b), HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    private HttpRequest authorized(HttpRequest.Builder builder) {
        return builder
                .header("Authorization", "Bearer " + token)
                .header(ApiRequestHeaderFilter.HEADER, ApiRequestHeaderFilter.VALUE)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    private static JSONObject unzipInput(byte[] pack, Path excel, Path jobDir) throws Exception {
        JSONObject meta = new JSONObject();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(pack))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();
                if ("job.json".equals(entry.getName())) {
                    meta = new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
                } else if ("suite.xlsx".equals(entry.getName())) {
                    Files.write(excel, data);
                } else {
                    Path dest = jobDir.resolve(entry.getName()).normalize();
                    if (dest.startsWith(jobDir)) {
                        Files.createDirectories(dest.getParent());
                        Files.write(dest, data);
                    }
                }
            }
        }
        return meta;
    }

    private static byte[] zipDir(Path dir) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos);
             var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    zos.putNextEntry(new ZipEntry(dir.relativize(p).toString().replace('\\', '/')));
                    zos.write(Files.readAllBytes(p));
                    zos.closeEntry();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return bos.toByteArray();
    }

    private static String envOrArg(String[] args, String flag, String env, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        String fromEnv = System.getenv(env);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return fallback;
    }
}
