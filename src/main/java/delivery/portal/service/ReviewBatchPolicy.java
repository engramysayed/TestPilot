package delivery.portal.service;

import java.util.ArrayList;
import java.util.List;

/**
 * How many cases one authoring-review HTTP call may carry, and how a larger
 * selection is split. Split starts only when the suite has more than
 * {@link #NO_SPLIT_MAX} cases.
 */
public final class ReviewBatchPolicy {
    public static final int ABSOLUTE_MAX = 50;
    public static final int NO_SPLIT_MAX = 5;
    public static final int DEFAULT_N = 50;

    private ReviewBatchPolicy() {
    }

    public static int clampN(int n) {
        if (n < 1) {
            return DEFAULT_N;
        }
        return Math.min(ABSOLUTE_MAX, n);
    }

    /** Cap for a single {@code reviewCases} POST. */
    public static int capForPostedCount(int posted, int configuredN) {
        int n = clampN(configuredN);
        if (posted <= NO_SPLIT_MAX) {
            return NO_SPLIT_MAX;
        }
        return n;
    }

    public static boolean exceedsCap(int posted, int configuredN) {
        return posted > capForPostedCount(posted, configuredN);
    }

    public static <T> List<List<T>> chunks(List<T> items, int configuredN) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (items.size() <= NO_SPLIT_MAX) {
            return List.of(List.copyOf(items));
        }
        int n = clampN(configuredN);
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += n) {
            int end = Math.min(items.size(), i + n);
            out.add(List.copyOf(items.subList(i, end)));
        }
        return List.copyOf(out);
    }
}
