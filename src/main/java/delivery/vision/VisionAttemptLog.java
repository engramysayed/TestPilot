package delivery.vision;

import java.util.ArrayList;
import java.util.List;

public final class VisionAttemptLog {

    private static final int MAX = 8;
    private static final ThreadLocal<List<VisionAttempt>> CURRENT =
            ThreadLocal.withInitial(ArrayList::new);

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
        if (list.size() >= MAX) {
            return;
        }
        list.add(attempt);
    }

    public static List<String> linesForHeal() {
        if (!VisionGroundingConfig.healHintsEnabled()) {
            return List.of();
        }
        return CURRENT.get().stream()
                .map(VisionAttempt::formatLine)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.length() > 200 ? s.substring(0, 200) : s)
                .toList();
    }

    public static void endIntent() {
        CURRENT.remove();
    }
}
