package delivery.vision;

import java.util.ArrayList;
import java.util.List;

public final class VisionAttemptLog {

    private static final int MAX = 8;
    /**
     * Attempts are scoped to one intent (ProvePhase: beginIntent ... endIntent). Outside a scope nothing
     * is recorded or returned: before r2 a lazily created per-thread list collected records from any
     * vision call (Hunt, tests, pooled worker threads) and leaked them into the next heal prompt.
     */
    private static final ThreadLocal<List<VisionAttempt>> CURRENT = new ThreadLocal<>();

    private VisionAttemptLog() {
    }

    public static void beginIntent() {
        CURRENT.set(new ArrayList<>());
    }

    public static void record(VisionAttempt attempt) {
        if (attempt == null) {
            return;
        }
        List<VisionAttempt> list = CURRENT.get();
        if (list == null || list.size() >= MAX) {
            return;
        }
        list.add(attempt);
    }

    public static List<String> linesForHeal() {
        if (!VisionGroundingConfig.healHintsEnabled()) {
            return List.of();
        }
        List<VisionAttempt> list = CURRENT.get();
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .map(VisionAttempt::formatLine)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.length() > 200 ? s.substring(0, 200) : s)
                .toList();
    }

    /** True between beginIntent and endIntent on this thread. */
    public static boolean inIntent() {
        return CURRENT.get() != null;
    }

    public static void endIntent() {
        CURRENT.remove();
    }
}
