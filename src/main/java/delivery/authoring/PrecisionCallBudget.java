package delivery.authoring;

/** Per-job cap on Cursor Precision calls (groundRank + solve). */
public final class PrecisionCallBudget {
    private final int cap;
    private int used;

    public PrecisionCallBudget(int cap) {
        this.cap = Math.max(0, cap);
    }

    public static PrecisionCallBudget fromProperties(boolean enabled, int maxCallsPerJob) {
        if (!enabled) {
            return new PrecisionCallBudget(0);
        }
        return new PrecisionCallBudget(maxCallsPerJob <= 0 ? 50 : maxCallsPerJob);
    }

    public boolean tryConsume() {
        if (used >= cap) {
            return false;
        }
        used++;
        return true;
    }

    public int used() {
        return used;
    }

    public int remaining() {
        return Math.max(0, cap - used);
    }

    public int cap() {
        return cap;
    }

    public boolean exhausted() {
        return used >= cap;
    }
}
