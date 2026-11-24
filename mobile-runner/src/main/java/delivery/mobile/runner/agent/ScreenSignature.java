package delivery.mobile.runner.agent;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Names the screen from the Android activity, then from what is visible.
 * A new activity is a new screen class even when the titles look alike.
 */
public final class ScreenSignature {

    private ScreenSignature() {
    }

    public static String of(MobileSnapshot snap, String appPackage) {
        String activity = activityStem(snap);
        String visible = visibleKey(snap, appPackage);
        if (activity.isBlank()) {
            return visible;
        }
        if (visible.isBlank()) {
            return activity;
        }
        return activity + visible;
    }

    /** The activity class, such as LoginActivity. Blank when the phone did not report one. */
    static String activityStem(MobileSnapshot snap) {
        if (snap == null || snap.meta() == null || snap.meta().activity() == null) {
            return "";
        }
        String act = snap.meta().activity().trim();
        if (act.isEmpty()) {
            return "";
        }
        int slash = act.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < act.length()) {
            act = act.substring(slash + 1);
        }
        int dot = act.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < act.length()) {
            act = act.substring(dot + 1);
        }
        String cleaned = act.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.isEmpty() || "Screen".equalsIgnoreCase(cleaned) || "Page".equalsIgnoreCase(cleaned)) {
            return "";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "A" + cleaned;
        }
        return cleaned;
    }

    private static String visibleKey(MobileSnapshot snap, String appPackage) {
        if (snap == null || snap.nodes() == null || snap.nodes().isEmpty()) {
            return "";
        }
        String pkg = appPackage == null ? "" : appPackage;
        List<String> ids = new ArrayList<>();
        String title = "";
        for (MobileSnapshotNode node : snap.nodes()) {
            if (node == null || !node.displayed()) {
                continue;
            }
            if (!pkg.isBlank() && !node.packageName().isBlank() && !node.packageName().equals(pkg)) {
                continue;
            }
            if (!node.resourceId().isBlank()) {
                ids.add(node.resourceId());
            }
            if (title.isBlank() && !node.clickable() && node.text().length() >= 2 && node.text().length() <= 40) {
                title = node.text();
            }
        }
        Collections.sort(ids);
        String sig = String.join("|", ids);
        if (sig.isBlank() && title.isBlank()) {
            return "";
        }
        String stem = title.isBlank() ? "Screen" : words(title);
        String hash = Integer.toHexString(sig.hashCode());
        if (hash.startsWith("-")) {
            hash = hash.substring(1);
        }
        if (hash.length() > 4) {
            hash = hash.substring(0, 4);
        }
        return stem + hash;
    }

    private static String words(String title) {
        String[] parts = title.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String cleaned = part.replaceAll("[^A-Za-z0-9]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(cleaned.charAt(0)));
            if (cleaned.length() > 1) {
                out.append(cleaned.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? "Screen" : out.toString();
    }
}
