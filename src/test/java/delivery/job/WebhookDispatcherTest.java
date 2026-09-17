package delivery.job;

import com.sun.net.httpserver.HttpServer;
import delivery.portal.model.JobRecord;
import delivery.portal.security.JobSecretCrypto;
import delivery.portal.service.WebhookDispatcher;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WebhookDispatcherTest {

    private String previousMode;
    private String previousCidrs;

    @BeforeMethod
    public void snapshotInstallPolicy() {
        previousMode = System.getProperty("delivery.install.mode");
        previousCidrs = System.getProperty("delivery.install.private-cidrs");
    }

    @AfterMethod
    public void restoreInstallPolicy() {
        restoreProp("delivery.install.mode", previousMode);
        restoreProp("delivery.install.private-cidrs", previousCidrs);
    }

    private static void restoreProp(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void dedicatedLoopback() {
        System.setProperty("delivery.install.mode", "dedicated");
        System.setProperty("delivery.install.private-cidrs", "127.0.0.0/8");
    }

    @Test
    public void retriesFailedDeliveryThenSkipsDuplicateDeliveryId() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        List<String> signatures = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            int n = hits.incrementAndGet();
            signatures.add(exchange.getRequestHeaders().getFirst("X-Keel-Signature"));
            exchange.getRequestBody().readAllBytes();
            if (n < 3) {
                exchange.sendResponseHeaders(500, 0);
            } else {
                byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, ok.length);
                exchange.getResponseBody().write(ok);
            }
            exchange.close();
        });
        server.start();
        dedicatedLoopback();
        try {
            Path root = Files.createTempDirectory("webhook-disp");
            Path destFile = root.resolve("webhook.json");
            WebhookDestinationStore store = new WebhookDestinationStore(destFile);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            store.put(url, "whsec",
                    delivery.net.TargetNetworkPolicy.dedicated(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                            java.util.List.of("127.0.0.0/8")));
            WebhookDispatcher dispatcher = new WebhookDispatcher(
                    store, root.resolve("deliveries"), Duration.ZERO);
            JobRecord job = new JobRecord(
                    "job_wh_1", "prj_1", 1L, "NEW", Path.of("in.xlsx"),
                    "https://example", "", "", false, JobRecord.JobKind.EXECUTE);
            job.setTenantId("ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            job.setStatus(JobRecord.Status.FAILED);
            job.setLibraryRevisionId("rev_1");

            dispatcher.deliver(job);
            Assert.assertEquals(hits.get(), 3);
            Assert.assertTrue(signatures.get(2).startsWith("sha256="));
            dispatcher.deliver(job);
            Assert.assertEquals(hits.get(), 3, "duplicate delivery id must not POST again");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void missingDestinationIsANoOp() throws Exception {
        Path root = Files.createTempDirectory("webhook-empty");
        WebhookDispatcher dispatcher = new WebhookDispatcher(
                new WebhookDestinationStore(root.resolve("webhook.json")),
                root.resolve("deliveries"),
                Duration.ZERO);
        JobRecord job = new JobRecord(
                "job_wh_none", "prj_1", 1L, "NEW", Path.of("in.xlsx"),
                "https://example", "", "");
        job.setStatus(JobRecord.Status.COMPLETED);
        dispatcher.deliver(job);
    }

    @Test
    public void stopsAfterMaxAttemptsWhenReceiverNeverSucceeds() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        server.start();
        dedicatedLoopback();
        try {
            Path root = Files.createTempDirectory("webhook-max");
            WebhookDestinationStore store = new WebhookDestinationStore(root.resolve("webhook.json"));
            store.put("http://127.0.0.1:" + server.getAddress().getPort() + "/hook", "whsec",
                    delivery.net.TargetNetworkPolicy.dedicated(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                            java.util.List.of("127.0.0.0/8")));
            WebhookDispatcher dispatcher = new WebhookDispatcher(
                    store, root.resolve("deliveries"), Duration.ZERO);
            JobRecord job = new JobRecord(
                    "job_wh_max", "prj_1", 1L, "NEW", Path.of("in.xlsx"),
                    "https://example", "", "", false, JobRecord.JobKind.EXECUTE);
            job.setTenantId("ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            job.setStatus(JobRecord.Status.FAILED);
            dispatcher.deliver(job);
            Assert.assertEquals(hits.get(), WebhookSigner.MAX_ATTEMPTS);
            dispatcher.deliver(job);
            Assert.assertEquals(hits.get(), WebhookSigner.MAX_ATTEMPTS, "failed delivery id still recorded");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void sharedPolicyDoesNotPostToRestrictedHopsEvenIfDestinationFileExists() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        System.setProperty("delivery.install.mode", "shared");
        System.clearProperty("delivery.install.private-cidrs");
        try {
            Path root = Files.createTempDirectory("webhook-ssrf");
            Path destFile = root.resolve("webhook.json");
            JSONObject row = new JSONObject();
            row.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
            row.put("secretCipher", JobSecretCrypto.encrypt("whsec"));
            row.put("enabled", true);
            Files.writeString(destFile, row.toString(2), StandardCharsets.UTF_8);
            WebhookDispatcher dispatcher = new WebhookDispatcher(
                    new WebhookDestinationStore(destFile),
                    root.resolve("deliveries"),
                    Duration.ZERO);
            JobRecord job = new JobRecord(
                    "job_wh_ssrf", "prj_1", 1L, "NEW", Path.of("in.xlsx"),
                    "https://shop.example.com/app", "", "", false, JobRecord.JobKind.EXECUTE);
            job.setTenantId("ws_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            job.setStatus(JobRecord.Status.FAILED);
            dispatcher.deliver(job);
            Assert.assertEquals(hits.get(), 0, "shared installs must not POST to loopback");
        } finally {
            server.stop(0);
        }
    }
}
