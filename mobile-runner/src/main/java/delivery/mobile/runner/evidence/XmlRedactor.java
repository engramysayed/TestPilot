package delivery.mobile.runner.evidence;

import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

/**
 * Produces redacted hierarchy XML for evidence. Password / canary / secret-looking text stripped.
 */
public final class XmlRedactor {
    public static final String REDACTED = "(redacted)";
    /** Canary string that must never appear in published evidence. */
    public static final String SECRET_CANARY = "CANARY_SECRET_VALUE_DO_NOT_LEAK";

    private XmlRedactor() {}

    public static String toRedactedXml(MobileSnapshot snap) {
        StringBuilder sb = new StringBuilder();
        sb.append("<hierarchy redacted=\"true\">\n");
        for (MobileSnapshotNode n : snap.nodes()) {
            sb.append("  <node index=\"").append(n.index()).append('"');
            attr(sb, "class", n.className());
            attr(sb, "resource-id", n.resourceId());
            attr(sb, "content-desc", redactField(n.contentDesc()));
            attr(sb, "text", n.password() ? REDACTED : redactField(n.rawText()));
            attr(sb, "hint", redactField(n.hint()));
            if (n.password()) sb.append(" password=\"true\"");
            if (n.bounds() != null) attr(sb, "bounds", n.bounds().toString());
            sb.append("/>\n");
        }
        sb.append("</hierarchy>\n");
        String out = sb.toString();
        if (out.contains(SECRET_CANARY)) {
            out = out.replace(SECRET_CANARY, REDACTED);
        }
        return out;
    }

    private static String redactField(String v) {
        if (v == null || v.isEmpty()) return "";
        if (v.contains(SECRET_CANARY)) return REDACTED;
        if (looksSecret(v)) return REDACTED;
        return v;
    }

    private static boolean looksSecret(String v) {
        String l = v.toLowerCase();
        return l.contains("password=") || l.startsWith("sk_") || l.contains("bearer ");
    }

    private static void attr(StringBuilder sb, String name, String value) {
        if (value == null || value.isEmpty()) return;
        sb.append(' ').append(name).append("=\"").append(xmlEscape(value)).append('"');
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
