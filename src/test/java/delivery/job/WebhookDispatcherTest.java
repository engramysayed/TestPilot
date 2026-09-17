package delivery.job;

import com.sun.net.httpserver.HttpServer;
import delivery.portal.model.JobRecord;
import delivery.portal.service.WebhookDispatcher;
import org.testng.Assert;
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
        try {
            Path root = Files.createTempDirectory("webhook-disp");
            Path destFile = root.resolve("webhook.json");
            WebhookDestinationStore store = new WebhookDestinationStore(destFile);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
            store.put(url, "whsec");
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
}
