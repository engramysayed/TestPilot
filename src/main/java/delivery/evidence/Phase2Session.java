package delivery.evidence;

import java.nio.file.Path;

/**
 * Per-prove/execute ThreadLocal: project disk root + job/run id for Phase 2 disk layout.
 * Same projectRoot Backend uses for execute-runs / heal-proposals / flaky-tags.
 */
public final class Phase2Session {
    private record State(Path projectRoot, String jobId, String projectId, String source) {
    }

    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private Phase2Session() {
    }

    public static Scope activate(Path projectRoot, String jobId, String projectId, String source) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(projectRoot, jobId, projectId, source == null ? "prove" : source));
        return new Scope(previous);
    }

    public static Path projectRoot() {
        State s = ACTIVE.get();
        return s == null ? null : s.projectRoot();
    }

    public static String jobId() {
        State s = ACTIVE.get();
        return s == null ? null : s.jobId();
    }

    public static String projectId() {
        State s = ACTIVE.get();
        return s == null ? null : s.projectId();
    }

    /** {@code prove} or {@code execute}. */
    public static String source() {
        State s = ACTIVE.get();
        return s == null ? "prove" : s.source();
    }

    public static final class Scope implements AutoCloseable {
        private final State previous;

        private Scope(State previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }
}
