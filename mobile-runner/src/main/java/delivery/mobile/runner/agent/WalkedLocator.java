package delivery.mobile.runner.agent;

import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Locator taken from the node a step actually bound on the phone.
 * A shell is the Excel label only — used when the bind missed or no phone was walked.
 */
public record WalkedLocator(String strategy, String value, boolean shell) {

    public WalkedLocator {
        strategy = strategy == null || strategy.isBlank() ? "text" : strategy;
        value = value == null ? "" : value;
    }

    public static WalkedLocator shell(String label) {
        String v = label == null ? "" : label.trim();
        if (v.isEmpty()) {
            v = "unknown";
        }
        return new WalkedLocator("text", v, true);
    }

    /** Resource id, then content-desc, then visible text. Otherwise a text shell of {@code fallback}. */
    public static WalkedLocator fromNode(MobileSnapshotNode node, String fallback) {
        if (node == null) {
            return shell(fallback);
        }
        String id = node.resourceId() == null ? "" : node.resourceId().trim();
        if (!id.isEmpty()) {
            return new WalkedLocator("id", id, false);
        }
        String desc = node.contentDesc() == null ? "" : node.contentDesc().trim();
        if (!desc.isEmpty()) {
            return new WalkedLocator("accessibilityId", desc, false);
        }
        String text = node.text() == null ? "" : node.text().trim();
        if (!text.isEmpty()) {
            return new WalkedLocator("text", text, false);
        }
        return shell(fallback);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("strategy", strategy);
        m.put("value", value);
        m.put("shell", shell);
        return m;
    }
}
