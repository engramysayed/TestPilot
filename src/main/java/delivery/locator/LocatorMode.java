package delivery.locator;

import java.util.Locale;

/**
 * Per-project choice of which locator family becomes the primary when several verified candidates exist.
 * Preferred hooks always come first in both modes.
 */
public enum LocatorMode {
    /**
     * Default. Survives copy changes and language switches (Arabic/English UIs):
     * hook → test id → unique human id → unique name → role+name → label → text → attribute CSS/XPath → ordinal.
     */
    STABLE_FIRST("stable-first"),
    /**
     * Playwright-style, reads like the user sees the page; breaks when labels are translated or reworded:
     * hook → role+name → label → test id → text → id → name → attribute CSS/XPath → ordinal.
     */
    USER_FACING_FIRST("user-facing-first");

    private final String wire;

    LocatorMode(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static LocatorMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return STABLE_FIRST;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return "user-facing-first".equals(v) || "user-facing".equals(v) || "semantic".equals(v)
                ? USER_FACING_FIRST : STABLE_FIRST;
    }
}
