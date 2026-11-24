package delivery.mobile.runner.agent;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Names the screen from what is visible, so a new page becomes a new screen class. */
public final class ScreenSignature {

    private ScreenSignature() {
    }

    public static String of(MobileSnapshot snap, String appPackage) {
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
