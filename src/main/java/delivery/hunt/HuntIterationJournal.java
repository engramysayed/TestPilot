package delivery.hunt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-iteration test plan and results artifacts ({@code test-plan.md}, {@code iteration-results.md}).
 */
public final class HuntIterationJournal {
    public static final String PLAN_FILE = "test-plan.md";
    public static final String RESULTS_FILE = "iteration-results.md";

    private final Path iterationDir;
    private final int iterationIndex;
    private final List<Map<String, Object>> scenarios = new ArrayList<>();
    private final List<Map<String, Object>> bugs = new ArrayList<>();
    private final StringBuilder executionNotes = new StringBuilder();

    public HuntIterationJournal(Path iterationDir, int iterationIndex) {
        this.iterationDir = iterationDir;
        this.iterationIndex = iterationIndex;
    }

    public void noteScenario(Map<String, Object> scenario) {
        if (scenario != null && !scenario.isEmpty()) {
            scenarios.add(scenario);
        }
    }

    public void noteBug(Map<String, Object> bug) {
        if (bug != null && !bug.isEmpty()) {
            bugs.add(bug);
        }
    }

    public void appendExecutionNote(String line) throws Exception {
        if (line == null || line.isBlank()) {
            return;
        }
        executionNotes.append(line.trim()).append('\n');
    }

    public String planMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Iteration ").append(iterationIndex).append(" test plan\n\n");
        if (scenarios.isEmpty()) {
            sb.append("_No scenarios emitted yet — planner should invent 2–3 cases this iteration._\n");
        } else {
            int i = 1;
            for (Map<String, Object> sc : scenarios) {
                sb.append("## Case ").append(i++).append(": ")
                        .append(sc.getOrDefault("title", "untitled")).append('\n');
                Object steps = sc.get("steps");
                if (steps != null) {
                    sb.append("Steps: ").append(steps).append('\n');
                }
                Object expected = sc.get("expected");
                if (expected != null) {
                    sb.append("Expected: ").append(expected).append('\n');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    public String resultsMarkdown(String stopReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Iteration ").append(iterationIndex).append(" results\n\n");
        sb.append("Stop: ").append(stopReason == null ? "" : stopReason).append('\n');
        sb.append("Scenarios: ").append(scenarios.size()).append('\n');
        sb.append("Bugs this iteration: ").append(bugs.size()).append("\n\n");
        if (!executionNotes.isEmpty()) {
            sb.append("## Execution notes\n\n").append(executionNotes).append('\n');
        }
        if (!bugs.isEmpty()) {
            sb.append("## Findings\n\n");
            for (Map<String, Object> b : bugs) {
                sb.append("- **").append(b.getOrDefault("title", "bug")).append("** (")
                        .append(b.getOrDefault("severity", "unknown")).append(")\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public void flushPlan() throws Exception {
        Files.createDirectories(iterationDir);
        Files.writeString(iterationDir.resolve(PLAN_FILE), planMarkdown(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public void flushResults(String stopReason) throws Exception {
        Files.createDirectories(iterationDir);
        Files.writeString(iterationDir.resolve(RESULTS_FILE), resultsMarkdown(stopReason), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public static String readResults(Path huntRoot, int iterationIndex) {
        Path file = huntRoot.resolve("iterations")
                .resolve(String.format(java.util.Locale.ROOT, "iteration-%02d", iterationIndex))
                .resolve(RESULTS_FILE);
        try {
            if (Files.isRegularFile(file)) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
