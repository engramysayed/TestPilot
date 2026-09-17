package delivery.portal.service;

import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import delivery.job.WebhookDestinationStore;
import delivery.job.WebhookSigner;
import delivery.net.TargetNetworkPolicy;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/** POSTs signed job-status payloads with bounded retries and delivery-id dedupe. */
@Component
public class WebhookDispatcher {
    private static final Logger log = LogManager.getLogger(WebhookDispatcher.class);

    private final Path storeRoot;
    private final Duration retryUnit;
    private final WebhookDestinationStore overrideStore;
    private final Path overrideDeliveryDir;
    private final HttpClient http;

    @Autowired
    public WebhookDispatcher(DeliveryPortalProperties props) {
        this(Path.of(props.getStoreRoot()),
                Duration.ofMillis(Math.max(0, props.getWebhookRetryUnitMs())),
                null,
                null);
    }

    public WebhookDispatcher(WebhookDestinationStore store, Path deliveryDir, Duration retryUnit) {
        this(null, retryUnit == null ? Duration.ZERO : retryUnit, store, deliveryDir);
    }

    private WebhookDispatcher(
            Path storeRoot,
            Duration retryUnit,
            WebhookDestinationStore overrideStore,
            Path overrideDeliveryDir
    ) {
        this.storeRoot = storeRoot;
        this.retryUnit = retryUnit == null ? Duration.ZERO : retryUnit;
        this.overrideStore = overrideStore;
        this.overrideDeliveryDir = overrideDeliveryDir;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Async("webhookExecutor")
    public void deliverAsync(JobRecord job) {
        deliver(job);
    }

    public void deliver(JobRecord job) {
        if (job == null || !JobRecord.isTerminal(job.getStatus())) {
            return;
        }
        try {
            WebhookDestinationStore dest = destinationStore(job);
            var config = dest.get();
            if (config.isEmpty()) {
                return;
            }
            TargetNetworkPolicy.Decision decision = TargetNetworkPolicy.forJob(job.getBaseUrl())
                    .inspectOutbound(config.get().url());
            if (!decision.allowed()) {
                log.warn("Webhook destination blocked by network policy for {}: {}",
                        job.getJobId(), decision.reason());
                return;
            }
            String deliveryId = "wh_" + job.getJobId() + "_" + job.getStatus().name();
            Path logFile = deliveryDir(job).resolve(deliveryId + ".json");
            if (Files.isRegularFile(logFile)) {
                return;
            }
            String body = payload(job, deliveryId);
            String signature = WebhookSigner.headerValue(
                    WebhookSigner.signature(config.get().secret(), deliveryId, body));
            boolean ok = false;
            int attempts = 0;
            for (int attempt = 1; attempt <= WebhookSigner.MAX_ATTEMPTS; attempt++) {
                attempts = attempt;
                int code = post(config.get().url(), deliveryId, signature, body);
                if (code >= 200 && code < 300) {
                    ok = true;
                    break;
                }
                Duration delay = WebhookSigner.retryDelay(attempt);
                if (delay.isZero()) {
                    break;
                }
                sleep(delay);
            }
            writeLog(logFile, deliveryId, ok, attempts);
        } catch (Exception e) {
            log.warn("Webhook delivery skipped for {}: {}", job.getJobId(), e.getMessage());
        }
    }

    private WebhookDestinationStore destinationStore(JobRecord job) {
        if (overrideStore != null) {
            return overrideStore;
        }
        return new WebhookDestinationStore(projectRoot(job).resolve("notifications").resolve("webhook.json"));
    }

    private Path deliveryDir(JobRecord job) throws Exception {
        Path dir = overrideDeliveryDir != null
                ? overrideDeliveryDir
                : projectRoot(job).resolve("notifications").resolve("deliveries");
        Files.createDirectories(dir);
        return dir;
    }

    private Path projectRoot(JobRecord job) {
        return ScopePaths.projectRoot(
                storeRoot,
                TenantId.parse(job.getTenantId()),
                job.getProjectId());
    }

    private int post(String url, String deliveryId, String signature, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Keel-Signature", signature)
                    .header("X-Keel-Delivery-Id", deliveryId)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (Exception e) {
            log.debug("Webhook POST failed: {}", e.getMessage());
            return 0;
        }
    }

    private void sleep(Duration delay) {
        if (retryUnit.isZero() || delay == null || delay.isZero()) {
            return;
        }
        long millis = delay.toMillis();
        if (retryUnit.toMillis() != 1000L) {
            millis = delay.toSeconds() * retryUnit.toMillis();
        }
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String payload(JobRecord job, String deliveryId) {
        JSONObject body = new JSONObject();
        body.put("deliveryId", deliveryId);
        body.put("jobId", job.getJobId());
        body.put("projectId", job.getProjectId());
        body.put("status", job.getStatus().name());
        body.put("parentJobId", job.getParentJobId() == null ? "" : job.getParentJobId());
        body.put("libraryRevisionId", job.getLibraryRevisionId() == null ? "" : job.getLibraryRevisionId());
        body.put("environmentRevisionId",
                job.getEnvironmentRevisionId() == null ? "" : job.getEnvironmentRevisionId());
        body.put("passedCount", job.getPassedCount());
        body.put("todoCount", job.getTodoCount());
        body.put("at", Instant.now().toString());
        return body.toString();
    }

    private static void writeLog(Path file, String deliveryId, boolean ok, int attempts) throws Exception {
        JSONObject row = new JSONObject();
        row.put("deliveryId", deliveryId);
        row.put("ok", ok);
        row.put("attempts", attempts);
        row.put("at", Instant.now().toString());
        Files.writeString(file, row.toString(2), StandardCharsets.UTF_8);
    }
}
