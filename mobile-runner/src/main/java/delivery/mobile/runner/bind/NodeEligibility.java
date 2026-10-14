package delivery.mobile.runner.bind;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

/**
 * System-bar / IME exclusion and action eligibility (arch §5.2–5.3).
 * System-bar nodes are dropped from candidates <em>and</em> anchors; systemui dialogs/heads-up kept.
 */
public final class NodeEligibility {
    private NodeEligibility() {}

    public static boolean isIme(MobileSnapshotNode n) {
        String p = n.packageName().toLowerCase();
        if (p.contains("inputmethod") || p.contains("keyboard")) return true;
        String w = n.windowId().toLowerCase();
        return "ime".equals(w) || w.contains("inputmethod");
    }

    /**
     * Status-icon / navigation band: systemui package hugging a screen edge with a short height/width.
     * Dialogs and heads-up (larger / centered / windowId=dialog) are not system bars.
     */
    public static boolean isSystemBar(MobileSnapshotNode n, int screenW, int screenH) {
        String p = n.packageName().toLowerCase();
        if (!p.contains("systemui")) return false;
        String w = n.windowId().toLowerCase();
        if ("dialog".equals(w) || w.contains("heads") || w.contains("toast") || w.contains("popup")) {
            return false;
        }
        MobileBounds b = n.bounds();
        if (b == null) return true; // conservative: treat unknown-geometry systemui as bar
        int h = b.height();
        int width = b.width();
        // Top/bottom status or nav band
        boolean topBand = b.top() <= 4 && h > 0 && h <= Math.max(120, screenH / 12);
        boolean bottomBand = b.bottom() >= screenH - 4 && h > 0 && h <= Math.max(160, screenH / 10);
        boolean sideBand = (b.left() <= 4 || b.right() >= screenW - 4)
                && width > 0 && width <= Math.max(80, screenW / 20);
        // Explicit status window
        if ("status".equals(w) || "navigation".equals(w) || "nav".equals(w)) return true;
        return topBand || bottomBand || sideBand;
    }

    /** Candidate pool membership (excludes IME + system bar). */
    public static boolean isCandidateEligible(MobileSnapshotNode n, MobileSnapshot snap) {
        int w = snap.meta().windowWidth() > 0 ? snap.meta().windowWidth() : 1080;
        int h = snap.meta().windowHeight() > 0 ? snap.meta().windowHeight() : 1920;
        if (isIme(n)) return false;
        if (isSystemBar(n, w, h)) return false;
        return true;
    }

    /** Anchor pool: same exclusions as candidates (status-bar must not affect binding). */
    public static boolean isAnchorEligible(MobileSnapshotNode n, MobileSnapshot snap) {
        return isCandidateEligible(n, snap);
    }

    public static boolean actionEligible(MobileSnapshotNode n, ActionKind action) {
        if (action == null || action == ActionKind.ANY || action == ActionKind.ASSERT) {
            return n.displayed();
        }
        return switch (action) {
            case TAP, LONG_PRESS -> n.displayed() && n.enabled() && (n.clickable() || n.longClickable() || n.checkable());
            case TYPE, CLEAR -> n.displayed() && n.enabled() && (n.focusable() || isEditLike(n));
            default -> n.displayed();
        };
    }

    public static boolean isEditLike(MobileSnapshotNode n) {
        String c = n.className().toLowerCase();
        return c.contains("edittext") || c.contains("textfield") || n.password() || !n.hint().isEmpty();
    }
}
