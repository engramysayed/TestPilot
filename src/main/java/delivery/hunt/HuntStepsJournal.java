package delivery.hunt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Growing steps journal for Bug Hunter memory (no DOM / screenshots — actions + results only).
 */
public final class HuntStepsJournal {
    public static final String FILE_NAME = "steps-journal.md";
    public static final int PROMPT_MAX_CHARS = 24_000;

    private final Path file;
    private final StringBuilder memory = new StringBuilder();

    public HuntStepsJournal(Path huntRoot) {
        this.file = huntRoot.resolve(FILE_NAME);
        memory.append("# Bug Hunter steps journal\n\n");
        memory.append("Actions taken and outcomes. No DOM or screenshots here.\n\n");
    }

    public void appendCycleHeader(int cycle, String decision, String rationale) throws Exception {
        String block = "## Cycle " + cycle + " — decision `" + decision + "`\n\n"
                + (rationale == null || rationale.isBlank() ? "" : "_Rationale:_ " + rationale.trim() + "\n\n");
        memory.append(block);
        flush();
    }

    public void appendActions(int cycle, List<Map<String, Object>> actionLog) throws Exception {
        if (actionLog == null || actionLog.isEmpty()) {
            memory.append("_No actions executed this cycle._\n\n");
            flush();
            return;
        }
        memory.append("### Actions\n\n");
        int i = 1;
        for (Map<String, Object> row : actionLog) {
            String type = str(row.get("type"));
            String status = str(row.get("status"));
            String reason = str(row.get("reason"));
            String detail = summarizeAction(row);
            memory.append(i++).append(". **").append(type).append("** → `").append(status).append("`");
            if (!detail.isBlank()) {
                memory.append(" — ").append(detail);
            }
            if (!reason.isBlank() && !"ok".equalsIgnoreCase(status)) {
                memory.append(" (").append(reason).append(")");
            }
            memory.append('\n');
        }
        memory.append('\n');
        flush();
    }

    public String forPrompt() {
        String all = memory.toString();
        if (all.length() <= PROMPT_MAX_CHARS) {
            return all;
        }
        return "…(earlier steps truncated)…\n" + all.substring(all.length() - PROMPT_MAX_CHARS);
    }

    /** Tail of the journal for bug repro slices (last ~2k chars). */
    public String reproSlice() {
        String all = memory.toString();
        int max = 2000;
        if (all.length() <= max) {
            return all;
        }
        return all.substring(all.length() - max);
    }

    public Path path() {
        return file;
    }

    private void flush() throws Exception {
        Files.writeString(file, memory.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String summarizeAction(Map<String, Object> row) {
        String type = str(row.get("type")).toLowerCase();
        return switch (type) {
            case "navigate" -> "url=" + str(row.get("url"));
            case "back", "forward", "refresh" -> "";
            case "execute_js" -> {
                String script = str(row.get("script"));
                if (script.isBlank()) {
                    script = str(row.get("code"));
                }
                if (script.isBlank()) {
                    script = str(row.get("js"));
                }
                String result = str(row.get("result"));
                yield "script=" + abbreviate(script, 80)
                        + (result.isBlank() ? "" : " result=" + abbreviate(result, 60));
            }
            case "type" -> "locator=" + locatorOf(row) + " value=" + str(row.get("value"));
            case "click", "clear", "assert_visible" -> "locator=" + locatorOf(row);
            case "assert_text" -> "text=" + (str(row.get("text")).isBlank() ? str(row.get("expected")) : str(row.get("text")));
            case "wait" -> "ms=" + str(row.get("ms"));
            default -> "";
        };
    }

    private static String locatorOf(Map<String, Object> row) {
        String loc = str(row.get("locator"));
        if (loc.isBlank()) {
            loc = str(row.get("locatorValue"));
        }
        return loc;
    }

    private static String abbreviate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max) + "…";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
