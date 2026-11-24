package delivery.mobile.runner.agent;

import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Chooses the next control Bug Hunter has not tapped, and notices a crash dialog. */
public final class MobileExplorePicker {

    private MobileExplorePicker() {
    }

    public static String key(MobileSnapshotNode node) {
        if (!node.resourceId().isBlank()) {
            return "id:" + node.resourceId();
        }
        if (!node.contentDesc().isBlank()) {
            return "desc:" + node.contentDescNormalized();
        }
        if (!node.text().isBlank()) {
            return "text:" + node.text();
        }
        return "at:" + node.bounds();
    }

    /** Index to tap, or -1 when every visible control was already tried. */
    public static int nextTap(List<MobileSnapshotNode> nodes, Set<String> seen) {
        if (nodes == null) {
            return -1;
        }
        for (MobileSnapshotNode node : nodes) {
            if (!node.displayed() || !node.enabled() || !node.clickable() || node.password()) {
                continue;
            }
            if (node.looksLikeSystemUi() || node.bounds() == null || node.bounds().isEmpty()) {
                continue;
            }
            String key = key(node);
            if (seen.contains(key)) {
                continue;
            }
            return node.index();
        }
        return -1;
    }

    public static String label(MobileSnapshotNode node) {
        if (!node.text().isBlank()) {
            return node.text();
        }
        if (!node.contentDesc().isBlank()) {
            return node.contentDescNormalized();
        }
        if (!node.resourceId().isBlank()) {
            return node.resourceId();
        }
        return node.className();
    }

    /** Non-blank when the screen is an Android crash or "isn't responding" dialog. */
    public static String crashText(List<MobileSnapshotNode> nodes) {
        if (nodes == null) {
            return "";
        }
        for (MobileSnapshotNode node : nodes) {
            String t = (node.text() + " " + node.contentDesc()).toLowerCase(Locale.ROOT);
            if (t.contains("isn't responding") || t.contains("isnt responding")
                    || t.contains("keeps stopping") || t.contains("has stopped")
                    || t.contains("unfortunately")) {
                String shown = node.text().isBlank() ? node.contentDescNormalized() : node.text();
                return shown.length() > 180 ? shown.substring(0, 180) : shown;
            }
        }
        return "";
    }
}
