package delivery.authoring;

/**
 * Builds XPath string literals safe for embedding in expressions. Values containing
 * {@code '} are emitted as {@code concat(...)} rather than stripped or broken.
 */
public final class XpathLiterals {

    private XpathLiterals() {
    }

    public static String quote(String text) {
        if (text == null) {
            return "''";
        }
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        String[] parts = text.split("'", -1);
        StringBuilder sb = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(",\"'\",");
            }
            sb.append("'").append(parts[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }
}
