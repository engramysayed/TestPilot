package delivery.runner;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;
import delivery.identity.TenantId;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.ConversionJobRunner;
import delivery.job.DryRunConversionService;
import delivery.job.DryRunExecuteService;
import delivery.job.ExecuteJobResult;
import delivery.job.ExecuteJobRunner;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;
import delivery.job.TenantScope;
import keel.contract.runner.WireJobKind;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * B07: the Phase 0a engine process. Generalises {@link PrivateRunnerAgent} onto Runner API v2 over
 * {@code http://localhost:8080} (runner token + HMAC). It never touches the portal DB or store: everything comes
 * from {@code /api/v2/runners/...} and results go back the same way (chunked artifacts, fenced result).
 *
 * <p>Kinds handled remotely in 0a: EXECUTE and CONVERT (see {@link #REMOTE_KINDS}). GENERATE_BATCH,
 * GENERATE_COMPARE and HUNT still need portal-side inputs (TcGenerateService, the project library) and stay
 * in-process until B07b moves those behind ports; the agent never claims them.
 */
public class EngineAgent {
    public static final Set<WireJobKind> REMOTE_KINDS = EnumSet.of(WireJobKind.EXECUTE, WireJobKind.CONVERT);
    static final int CHUNK = 4 * 1024 * 1024;

    /** Runs one claimed job's work; replaceable in tests. */
    public interface Worker {
        Outcome run(WireJobKind kind, ConversionJobRequest request, BooleanSupplier cancel) throws Exception;
    }

    public record Outcome(String status, int passed, int todo, String message) {
    }

    private final EngineConfig config;
    private final RunnerV2Client client;
    private final Worker worker;
    private final ScheduleClaimPoller scheduleClaim;

    public EngineAgent(EngineConfig config) {
        this(config, new RunnerV2Client(config.portalUrl(), config.token()), null, null);
    }

    public EngineAgent(EngineConfig config, RunnerV2Client client, Worker worker) {
        this(config, client, worker, null);
    }

    public EngineAgent(EngineConfig config, RunnerV2Client client, Worker worker, ScheduleClaimPoller scheduleClaim) {
        this.config = config;
        this.client = client;
        this.worker = worker == null ? this::defaultWorker : worker;
        this.scheduleClaim = scheduleClaim == null ? ScheduleClaimPoller.fromEnv(config) : scheduleClaim;
    }

    /** Shutdown flag flipped by the JVM shutdown hook so the run loop can drain cleanly. */
    private static volatile boolean shuttingDown = false;

    public static void main(String[] args) throws Exception {
        EngineConfig config;
        try {
            config = EngineConfig.from(args, System.getenv());
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        config.proxy().publish(System.getProperties(), config.portalUrl()); // B11, no-op unless enabled
        System.out.println("Keel engine " + config);
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shuttingDown = true;
            System.out.println("Keel engine: shutdown signal — draining");
            mainThread.interrupt();
        }, "keel-engine-shutdown"));
        new EngineAgent(config).runLoop();
    }

    public void runLoop() throws Exception {
        Files.createDirectories(config.workDir());
        while (!Thread.currentThread().isInterrupted() && !shuttingDown) {
            try {
                if (runOnce() == 0) {
                    Thread.sleep(config.pollMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("engine cycle: " + e.getMessage());
                try { Thread.sleep(config.pollMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }

    /** @return 1 if a job was claimed and handled, 0 if there was no work. */
    public int runOnce() throws Exception {
        if (scheduleClaim != null) {
            scheduleClaim.tick();
        }
        JSONObject claimReq = new JSONObject();
        JSONArray kinds = new JSONArray();
        REMOTE_KINDS.forEach(k -> kinds.put(k.name()));
        claimReq.put("kinds", kinds);
        RunnerV2Client.Response claim = client.send("POST", "/api/v2/runners/claim", null,
                claimReq.toString().getBytes(StandardCharsets.UTF_8), null);
        if (claim.status() == 204) {
            return 0;
        }
        if (claim.status() != 200) {
            throw new IllegalStateException("claim failed: " + claim.status() + " " + claim.text());
        }
        JSONObject c = new JSONObject(claim.text());
        RunnerV2Client.Fence fence = new RunnerV2Client.Fence(c.getString("jobId"), c.getString("attemptId"),
                c.getLong("cancelGeneration"), c.getLong("fencingToken"));
        handle(fence, WireJobKind.valueOf(c.getString("jobKind")), c.optString("inputSnapshotHash", ""),
                c.optString("providerAllowlistSnapshot", ""));
        return 1;
    }

    private String base(RunnerV2Client.Fence f) {
        return "/api/v2/runners/jobs/" + f.jobId();
    }

    void handle(RunnerV2Client.Fence fence, WireJobKind kind, String inputHash, String allowlist) throws Exception {
        RunnerV2Client.Response in = client.send("GET", base(fence) + "/input", null, null, fence);
        if (in.status() != 200) {
            throw new IllegalStateException("input rejected: " + in.status());
        }
        Path jobDir = config.workDir().resolve(fence.jobId()).resolve(fence.attemptId());
        Files.createDirectories(jobDir);
        Path excel = jobDir.resolve("suite.xlsx");
        JSONObject meta = unzipInput(in.body(), excel, jobDir);
        if (!config.dryRun()) {
            expectOk(client.send("POST", base(fence) + "/stage", null,
                    "{\"stage\":\"BROWSER\"}".getBytes(StandardCharsets.UTF_8), fence), "stage");
        }
        BooleanSupplier cancel = () -> cancelRequested(fence);
        Outcome outcome;
        if (cancel.getAsBoolean()) {
            outcome = new Outcome("CANCELLED", 0, 0, "Cancelled");
        } else {
            try {
                outcome = worker.run(kind, request(fence.jobId(), meta, excel, jobDir), cancel);
            } catch (JobCancelledException e) {
                outcome = new Outcome("CANCELLED", 0, 0, "Cancelled");
            } catch (Exception e) {
                outcome = new Outcome("FAILED", 0, 0, e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
        if (!"CANCELLED".equals(outcome.status())) {
            uploadChunked(fence, "evidence", zipDir(jobDir));
        }
        JSONObject result = new JSONObject();
        result.put("status", outcome.status());
        result.put("passedCount", outcome.passed());
        result.put("todoCount", outcome.todo());
        result.put("message", outcome.message() == null ? "" : outcome.message());
        result.put("inputSnapshotHash", inputHash.isBlank() ? meta.optString("inputSnapshotHash") : inputHash);
        result.put("providerAllowlistSnapshot", allowlist.isBlank() ? meta.optString("providerAllowlistSnapshot") : allowlist);
        expectOk(client.send("POST", base(fence) + "/result", null,
                result.toString().getBytes(StandardCharsets.UTF_8), fence), "result");
    }

    void uploadChunked(RunnerV2Client.Fence fence, String artifactId, byte[] bytes) throws Exception {
        int parts = Math.max(1, (bytes.length + CHUNK - 1) / CHUNK);
        for (int i = 0; i < parts; i++) {
            int from = i * CHUNK;
            byte[] part = java.util.Arrays.copyOfRange(bytes, Math.min(from, bytes.length),
                    Math.min(from + CHUNK, bytes.length));
            RunnerV2Client.Response r = client.send("PUT", base(fence) + "/artifacts/" + artifactId + "/chunks/" + i,
                    i == parts - 1 ? "last=true" : null, part, fence);
            if (r.status() != 202) {
                throw new IllegalStateException("chunk " + i + " rejected: " + r.status() + " " + r.text());
            }
        }
    }

    private boolean cancelRequested(RunnerV2Client.Fence fence) {
        try {
            RunnerV2Client.Response r = client.send("POST", base(fence) + "/lease", null, null, fence);
            if (r.status() != 200) {
                return true; // lease lost / fenced: stop working, the portal decides what happens to the job
            }
            return new JSONObject(r.text()).optBoolean("cancelRequested", false);
        } catch (Exception e) {
            return true;
        }
    }

    private ConversionJobRequest request(String jobId, JSONObject meta, Path excel, Path jobDir) {
        return new ConversionJobRequest(meta.optString("projectId"), excel, meta.optString("baseUrl"),
                meta.optString("username"), meta.optString("password"), jobDir, jobDir.resolve("store"),
                config.templateRoot().toAbsolutePath().normalize(), meta.optString("mode", "EXECUTE"), "", "", false, false,
                AuthoringEngine.parse(meta.optString("authoringEngine", "keel")),
                new PrecisionJobConfig(meta.optBoolean("precisionEnabled", false), meta.optInt("precisionMaxCalls", 50),
                        meta.optBoolean("precisionReuseLocatorMemory", false)),
                TenantId.parse(meta.getString("tenantId")), jobId, TenantScope.HOSTED);
    }

    private Outcome defaultWorker(WireJobKind kind, ConversionJobRequest request, BooleanSupplier cancel) throws Exception {
        switch (kind) {
            case EXECUTE -> {
                ExecuteJobResult r = config.dryRun()
                        ? new DryRunExecuteService().run(request, request.jobId(), new JobProgressTracker(), cancel)
                        : new ExecuteJobRunner().run(request, request.jobId(), cancel);
                return new Outcome("COMPLETED", r.passed(), r.todo(), r.message());
            }
            case CONVERT -> {
                ConversionJobResult r = config.dryRun()
                        ? new DryRunConversionService().run(request, new JobProgressTracker(), cancel)
                        : new ConversionJobRunner().run(request, cancel);
                return new Outcome("COMPLETED", r.passed(), r.todo(), r.message());
            }
            default -> throw new IllegalStateException("UNSUPPORTED_KIND " + kind + " (stays in-process in 0a)");
        }
    }

    private static void expectOk(RunnerV2Client.Response r, String what) {
        if (r.status() != 200) {
            throw new IllegalStateException(what + " rejected: " + r.status() + " " + r.text());
        }
    }

    static JSONObject unzipInput(byte[] pack, Path excel, Path jobDir) throws Exception {
        JSONObject meta = new JSONObject();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(pack))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();
                if ("job.json".equals(entry.getName())) {
                    meta = new JSONObject(new String(data, StandardCharsets.UTF_8));
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

    static byte[] zipDir(Path dir) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos); var walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                zos.putNextEntry(new ZipEntry(dir.relativize(p).toString().replace('\\', '/')));
                zos.write(Files.readAllBytes(p));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
