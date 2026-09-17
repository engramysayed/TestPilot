package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class WebhookAndIdempotencyTest {

    @Test
    public void webhookSignatureRetryAndDedupe() {
        String sig = WebhookSigner.signature("secret", "del_1", "{\"ok\":true}");
        Assert.assertEquals(sig, WebhookSigner.signature("secret", "del_1", "{\"ok\":true}"));
        Assert.assertNotEquals(sig, WebhookSigner.signature("secret", "del_2", "{\"ok\":true}"));
        Assert.assertEquals(WebhookSigner.retryDelay(1), Duration.ofSeconds(1));
        Assert.assertEquals(WebhookSigner.retryDelay(2), Duration.ofSeconds(4));
        Assert.assertEquals(WebhookSigner.retryDelay(5), Duration.ZERO);
        Assert.assertTrue(WebhookSigner.duplicate("del_1", "del_1"));
        Assert.assertFalse(WebhookSigner.duplicate("del_1", "del_2"));
        Assert.assertTrue(WebhookSigner.headerValue(sig).startsWith("sha256="));
    }

    @Test
    public void repeatedIdempotencyKeyReusesJobAndConflictsOnDifferentBody() throws Exception {
        Path file = Files.createTempDirectory("idem").resolve("keys.json");
        IdempotencyStore store = new IdempotencyStore(file);
        IdempotencyStore.Entry first = store.putOrGet("ci-1", "aaa", "job_1");
        IdempotencyStore.Entry again = store.putOrGet("ci-1", "aaa", "job_other");
        Assert.assertEquals(again.jobId(), first.jobId());
        Assert.assertThrows(IdempotencyStore.Conflict.class, () -> store.putOrGet("ci-1", "bbb", "job_2"));
    }

    @Test
    public void rateLimitAllowsDocumentedWindow() {
        RateLimitPolicy policy = new RateLimitPolicy();
        Instant now = Instant.parse("2026-09-17T12:00:00Z");
        for (int i = 0; i < RateLimitPolicy.REQUESTS_PER_MINUTE; i++) {
            Assert.assertTrue(policy.allow("ws_1", now));
        }
        Assert.assertFalse(policy.allow("ws_1", now));
        Assert.assertTrue(policy.allow("ws_2", now));
    }
}
