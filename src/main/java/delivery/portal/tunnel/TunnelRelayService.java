package delivery.portal.tunnel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Cloud tunnel broker: portal queues HTTP; agent polls, executes locally, completes. */
@Service
@ConditionalOnProperty(name = "delivery.tunnel.enabled", havingValue = "true")
public class TunnelRelayService {

    private static final int QUEUE_CAP = 32;

    private final ConcurrentHashMap<String, BlockingQueue<RelayRequest>> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<RelayResponse>> pending = new ConcurrentHashMap<>();

    public RelayResponse relay(
            String token,
            String method,
            String path,
            Map<String, String> headers,
            byte[] body,
            Duration timeout
    ) throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "");
        RelayRequest req = new RelayRequest(
                id, token, method, path, headers == null ? Map.of() : Map.copyOf(headers),
                body == null ? new byte[0] : body, Instant.now());
        BlockingQueue<RelayRequest> q = queues.computeIfAbsent(token, t -> new ArrayBlockingQueue<>(QUEUE_CAP));
        // Register the waiter BEFORE the agent can see the request; otherwise a fast agent completes
        // an id that is not yet pending (404) and the caller hangs until timeout.
        CompletableFuture<RelayResponse> future = new CompletableFuture<>();
        pending.put(id, future);
        if (!q.offer(req)) {
            pending.remove(id);
            throw new QueueFullException();
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            // Agent never picked it up: remove it so a late poll does not execute a request nobody awaits.
            q.remove(req);
            throw e;
        } finally {
            pending.remove(id);
        }
    }

    public Optional<RelayRequest> poll(String token, Duration maxWait) throws InterruptedException {
        BlockingQueue<RelayRequest> q = queues.computeIfAbsent(token, t -> new ArrayBlockingQueue<>(QUEUE_CAP));
        RelayRequest req = q.poll(maxWait.toMillis(), TimeUnit.MILLISECONDS);
        return Optional.ofNullable(req);
    }

    public boolean complete(String requestId, int status, byte[] body, Map<String, String> responseHeaders) {
        Map<String, java.util.List<String>> multi = new LinkedHashMap<>();
        if (responseHeaders != null) {
            responseHeaders.forEach((k, v) -> {
                if (k != null && v != null) {
                    multi.put(k, java.util.List.of(v));
                }
            });
        }
        return completeMulti(requestId, status, body, multi);
    }

    /** Multi-valued response headers (several {@code Set-Cookie} lines must all survive the relay). */
    public boolean completeMulti(
            String requestId, int status, byte[] body, Map<String, java.util.List<String>> responseHeaders) {
        CompletableFuture<RelayResponse> f = pending.remove(requestId);
        if (f == null) {
            return false;
        }
        Map<String, java.util.List<String>> copy = new LinkedHashMap<>();
        if (responseHeaders != null) {
            responseHeaders.forEach((k, v) -> {
                if (k != null && v != null && !v.isEmpty()) {
                    copy.put(k, java.util.List.copyOf(v));
                }
            });
        }
        f.complete(new RelayResponse(status, body == null ? new byte[0] : body, Map.copyOf(copy)));
        return true;
    }

    /** Drops queued requests for a token that is no longer enrolled (re-enroll). */
    public void forget(String token) {
        if (token != null) {
            queues.remove(token);
        }
    }

    public static final class QueueFullException extends IllegalStateException {
        public QueueFullException() {
            super("Tunnel relay queue full");
        }
    }

    public record RelayRequest(
            String requestId,
            String token,
            String method,
            String path,
            Map<String, String> headers,
            byte[] body,
            Instant enqueuedAt
    ) {
        public Map<String, Object> toAgentJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestId", requestId);
            m.put("method", method);
            m.put("path", path);
            m.put("headers", headers);
            m.put("bodyBase64", body.length == 0 ? "" : Base64.getEncoder().encodeToString(body));
            return m;
        }
    }

    public record RelayResponse(int status, byte[] body, Map<String, java.util.List<String>> headers) {
    }
}
