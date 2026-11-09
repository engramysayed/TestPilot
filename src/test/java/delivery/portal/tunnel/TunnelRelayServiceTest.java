package delivery.portal.tunnel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TunnelRelayServiceTest {

    @Test
    public void relayRoundTrip() throws Exception {
        TunnelRelayService relay = new TunnelRelayService();
        CompletableFuture<Void> agent = CompletableFuture.runAsync(() -> {
            try {
                var req = relay.poll("tok", Duration.ofSeconds(5));
                Assert.assertTrue(req.isPresent());
                relay.complete(req.get().requestId(), 200, "ok".getBytes(), Map.of("Content-Type", "text/plain"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        TunnelRelayService.RelayResponse res = relay.relay(
                "tok", "GET", "/hello", Map.of(), new byte[0], Duration.ofSeconds(5));
        agent.get(10, TimeUnit.SECONDS);
        Assert.assertEquals(res.status(), 200);
        Assert.assertEquals(new String(res.body()), "ok");
    }

    @Test
    public void fastAgentCompletionIsNotLost() throws Exception {
        // Agent is already blocked in poll before relay() enqueues: completion must find the waiter.
        TunnelRelayService relay = new TunnelRelayService();
        for (int i = 0; i < 50; i++) {
            CompletableFuture<Boolean> agent = CompletableFuture.supplyAsync(() -> {
                try {
                    var req = relay.poll("fast", Duration.ofSeconds(5));
                    return relay.complete(req.orElseThrow().requestId(), 204, new byte[0], Map.of());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(5);
            var res = relay.relay("fast", "GET", "/x", Map.of(), new byte[0], Duration.ofSeconds(5));
            Assert.assertEquals(res.status(), 204);
            Assert.assertTrue(agent.get(5, TimeUnit.SECONDS));
        }
    }

    @Test(expectedExceptions = java.util.concurrent.TimeoutException.class)
    public void timeoutRemovesUnclaimedRequest() throws Exception {
        TunnelRelayService relay = new TunnelRelayService();
        try {
            relay.relay("nobody", "GET", "/x", Map.of(), new byte[0], Duration.ofMillis(200));
        } finally {
            Assert.assertTrue(relay.poll("nobody", Duration.ofMillis(50)).isEmpty(),
                    "timed-out request must not be delivered to a late agent");
        }
    }

    @Test
    public void multipleSetCookieValuesSurvive() throws Exception {
        TunnelRelayService relay = new TunnelRelayService();
        CompletableFuture<Void> agent = CompletableFuture.runAsync(() -> {
            try {
                var req = relay.poll("multi", Duration.ofSeconds(5)).orElseThrow();
                relay.completeMulti(req.requestId(), 200, new byte[0], Map.of(
                        "Set-Cookie", java.util.List.of("sid=1; Path=/", "csrf=2; Path=/")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        var res = relay.relay("multi", "POST", "/login", Map.of(), new byte[0], Duration.ofSeconds(5));
        agent.get(5, TimeUnit.SECONDS);
        Assert.assertEquals(res.headers().get("Set-Cookie"), java.util.List.of("sid=1; Path=/", "csrf=2; Path=/"));
    }
}
