package delivery.authoring;

import java.io.IOException;
import java.time.Duration;

/** Bounds consecutive model requests on the calling thread without losing provider policy scope. */
public final class LlmCallDeadline {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private LlmCallDeadline() { }

    public static Scope within(Duration budget) {
        Long previous = DEADLINE.get();
        long end = System.nanoTime() + budget.toNanos();
        DEADLINE.set(previous == null ? end : Math.min(previous, end));
        return () -> { if (previous == null) DEADLINE.remove(); else DEADLINE.set(previous); };
    }

    public static Duration limit(Duration configured) throws IOException {
        Long end = DEADLINE.get();
        if (end == null) return configured;
        long remaining = end - System.nanoTime();
        if (remaining <= 0) throw new IOException("model operation deadline exceeded");
        return Duration.ofNanos(Math.min(configured.toNanos(), remaining));
    }

    public interface Scope extends AutoCloseable { @Override void close(); }
}
