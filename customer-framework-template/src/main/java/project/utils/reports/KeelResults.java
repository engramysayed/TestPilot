package project.utils.reports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Machine-readable run facts for CI (verdicts.jsonl, locator-events.jsonl), one JSON object per
 * line under {@code test-output/keel-results} (override with -Dkeel.results.dir). Never throws.
 */
public final class KeelResults {
    public static final String VERDICTS = "verdicts.jsonl";
    public static final String LOCATOR_EVENTS = "locator-events.jsonl";

    private KeelResults() {
    }

    public static Path dir() {
        String override = System.getProperty("keel.results.dir");
        return override != null && !override.isBlank()
                ? Path.of(override)
                : Path.of(System.getProperty("user.dir"), "test-output", "keel-results");
    }

    public static synchronized void append(String file, Map<String, ?> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(e.getKey())).append(':');
            Object v = e.getValue();
            sb.append(v instanceof Number || v instanceof Boolean ? String.valueOf(v) : quote(String.valueOf(v)));
        }
        sb.append("}\n");
        String line = Secrets.redact(sb.toString()); // IR secret: true values never reach the jsonl files
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(file), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // reporting must never break a test
        }
    }

    public static synchronized void reset() {
        for (String f : new String[]{VERDICTS, LOCATOR_EVENTS}) {
            try {
                Files.deleteIfExists(dir().resolve(f));
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
