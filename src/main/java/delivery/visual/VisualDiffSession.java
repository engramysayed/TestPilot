package delivery.visual;

import java.nio.file.Path;

/**
 * Per-prove ThreadLocal: project disk root + run/job id for visual-diffs layout.
 * Activated by {@link delivery.job.ProvePhase} when the feature flag is on.
 */
public final class VisualDiffSession {
    private record State(Path projectRoot, String runId, String projectId) {
    }

    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private VisualDiffSession() {
    }

    public static Scope activate(Path projectRoot, String runId, String projectId) {
        State previous = ACTIVE.get();
        ACTIVE.set(new State(projectRoot, runId, projectId));
        return new Scope(previous);
    }

    public static Path projectRoot() {
        State s = ACTIVE.get();
        return s == null ? null : s.projectRoot();
    }

    public static String runId() {
        State s = ACTIVE.get();
        return s == null ? null : s.runId();
    }

    public static String projectId() {
        State s = ACTIVE.get();
        return s == null ? null : s.projectId();
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
