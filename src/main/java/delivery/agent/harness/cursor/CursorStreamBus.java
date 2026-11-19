package delivery.agent.harness.cursor;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process pub/sub for raw Cursor sidecar NDJSON lines, keyed by parent job id.
 *
 * <p>CursorSidecarSession.drainStdout calls {@link #push(String, String)} for every parsed line.
 * The frontend subscribes via {@code GET /api/jobs/{jobId}/cursor-stream} which hands back an
 * {@link SseEmitter}; we register it with {@link #subscribe(String, SseEmitter)}, fan each new line
 * to every active subscriber, and replay the recent backlog (bounded ring) to late joiners so a
 * refresh doesn't miss events emitted before the subscription opened.
 *
 * <p>No persistence — a JVM restart clears everything. That's intentional: Cursor token streams
 * are useful live, not post-mortem (the final result + job progress log already persist).
 */
public final class CursorStreamBus {

    public static final int BUFFER_MAX = 200;
    public static final int DEFAULT_SUBSCRIBER_TIMEOUT_MS = 600_000; // 10 min — long enough for most jobs

    private static final Map<String, Deque<Event>> BUFFERS = new ConcurrentHashMap<>();
    private static final Map<String, CopyOnWriteArrayList<SseEmitter>> SUBSCRIBERS = new ConcurrentHashMap<>();

    public record Event(long ts, String line) {}

    private CursorStreamBus() {}

    /** Append a line to the buffer for {@code jobId} and fan out to live subscribers. No-op when key is blank. */
    public static void push(String jobId, String line) {
        if (jobId == null || jobId.isBlank() || line == null) return;
        Event ev = new Event(System.currentTimeMillis(), line);
        BUFFERS.compute(jobId, (k, deque) -> {
            Deque<Event> d = deque == null ? new ArrayDeque<>(BUFFER_MAX) : deque;
            synchronized (d) {
                d.addLast(ev);
                while (d.size() > BUFFER_MAX) {
                    d.removeFirst();
                }
            }
            return d;
        });
        CopyOnWriteArrayList<SseEmitter> subs = SUBSCRIBERS.get(jobId);
        if (subs == null || subs.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : subs) {
            try {
                emitter.send(SseEmitter.event().name("cursor").data(line));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            subs.removeAll(dead);
        }
    }

    /** Register {@code emitter} for live events and replay the buffer. Clean-up on completion/timeout/error. */
    public static void subscribe(String jobId, SseEmitter emitter) {
        if (jobId == null || jobId.isBlank() || emitter == null) return;
        CopyOnWriteArrayList<SseEmitter> subs =
                SUBSCRIBERS.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>());
        subs.add(emitter);
        Runnable cleanup = () -> subs.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());
        // Replay what we have so a reload doesn't lose context.
        Deque<Event> deque = BUFFERS.get(jobId);
        if (deque == null) return;
        List<Event> snapshot;
        synchronized (deque) {
            snapshot = new ArrayList<>(deque);
        }
        for (Event ev : snapshot) {
            try {
                emitter.send(SseEmitter.event().name("cursor").data(ev.line()));
            } catch (IOException | IllegalStateException e) {
                subs.remove(emitter);
                return;
            }
        }
        // Heartbeat so idle connections don't get killed by proxies; one-shot "ready" marker.
        try {
            emitter.send(SseEmitter.event().name("ready").data(Instant.now().toString()));
        } catch (IOException | IllegalStateException e) {
            subs.remove(emitter);
        }
    }

    /** Snapshot of the buffer for {@code jobId}; empty if none. For tests + fallback polling. */
    public static List<Event> snapshot(String jobId) {
        Deque<Event> deque = BUFFERS.get(jobId);
        if (deque == null) return List.of();
        synchronized (deque) {
            return List.copyOf(deque);
        }
    }

    /** Drop the buffer + close all subscribers for {@code jobId}. Call when the job reaches terminal state. */
    public static void drop(String jobId) {
        if (jobId == null || jobId.isBlank()) return;
        BUFFERS.remove(jobId);
        CopyOnWriteArrayList<SseEmitter> subs = SUBSCRIBERS.remove(jobId);
        if (subs == null) return;
        for (SseEmitter emitter : subs) {
            try { emitter.complete(); } catch (Exception ignored) { /* already closed */ }
        }
    }

    /** Package-visible for tests: wipe all state. */
    static void resetForTests() {
        BUFFERS.clear();
        SUBSCRIBERS.clear();
    }
}
