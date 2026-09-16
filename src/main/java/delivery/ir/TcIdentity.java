package delivery.ir;

import delivery.excel.ManualTestCase;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One TC ID contract for portal, CLI, and direct conversion.
 * Display IDs stay as authored; {@link #storageKey(String)} is the on-disk name.
 */
public final class TcIdentity {
    /** Same rule as the generate quality gate: {@code TC_} plus digits or A-Z / digits / underscore. */
    public static final Pattern PATTERN = Pattern.compile("^TC_(\\d+|[A-Z0-9_]+)$");

    private TcIdentity() {
    }

    public static boolean isValid(String tcId) {
        return tcId != null && PATTERN.matcher(tcId.trim()).matches();
    }

    public static String invalidMessage(String tcId) {
        String id = tcId == null ? "" : tcId.trim();
        return "Invalid tcId '" + id + "': must match TC_<digits> or TC_<ALNUM_UNDERSCORE>";
    }

    public static void requireValid(String tcId) {
        if (!isValid(tcId)) {
            throw new IllegalArgumentException(invalidMessage(tcId));
        }
    }

    public static void requireValidCases(List<ManualTestCase> cases) {
        if (cases == null) {
            return;
        }
        for (ManualTestCase tc : cases) {
            if (tc == null) {
                continue;
            }
            requireValid(tc.tcId());
        }
    }

    /** Original display ID (trimmed). Stored inside IR JSON as {@code tcId}. */
    public static String displayId(String tcId) {
        return tcId == null ? "" : tcId.trim();
    }

    /**
     * Collision-resistant filename stem. Encodes every non-alphanumeric byte as {@code ~HH}
     * so {@code TC/1} and {@code TC_1} cannot share a file.
     */
    public static String storageKey(String tcId) {
        String id = displayId(tcId);
        if (id.isEmpty()) {
            return "tc";
        }
        StringBuilder sb = new StringBuilder(id.length() * 2);
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else {
                sb.append('~');
                sb.append(String.format("%02X", (int) c));
            }
        }
        return sb.toString();
    }
}
