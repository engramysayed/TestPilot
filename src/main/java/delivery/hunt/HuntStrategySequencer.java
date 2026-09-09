package delivery.hunt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed-order playbook modes for Bug Hunter Phase 2 strategy sequencing. */
public final class HuntStrategySequencer {

    private static final List<String> MODES = List.of(
            "happy", "empty", "boundary", "abuse", "session", "invent");

    private static final Map<String, String> GOALS = goals();

    private final boolean strategiesEnabled;
    private int index;
    private final List<String> completed = new ArrayList<>();

    public HuntStrategySequencer(boolean strategiesEnabled) {
        this.strategiesEnabled = strategiesEnabled;
    }

    public record Hint(String mode, String goal, List<String> completed) {
    }

    public Hint current() {
        String mode = MODES.get(index);
        return new Hint(mode, GOALS.get(mode), List.copyOf(completed));
    }

    public String forPrompt() {
        Hint hint = current();
        StringBuilder sb = new StringBuilder();
        sb.append("## Hunt strategy\n");
        sb.append("mode=").append(hint.mode()).append('\n');
        sb.append("goal=").append(hint.goal()).append('\n');
        sb.append("completedModes=");
        if (completed.isEmpty()) {
            sb.append("none");
        } else {
            sb.append(String.join(",", completed));
        }
        sb.append('\n');
        return sb.toString();
    }

    public void advance() {
        if (index < MODES.size() - 1) {
            completed.add(MODES.get(index));
            index++;
        }
    }

    public boolean isLast() {
        return index >= MODES.size() - 1;
    }

    /** Marks the current mode complete without advancing (used for invent budget met). */
    public void completeCurrent() {
        String mode = MODES.get(index);
        if (!completed.contains(mode)) {
            completed.add(mode);
        }
    }

    /** True when every playbook mode has been marked complete. */
    public boolean finishedAll() {
        return strategiesEnabled && completed.size() == MODES.size();
    }

    public List<String> completed() {
        return Collections.unmodifiableList(completed);
    }

    public boolean strategiesEnabled() {
        return strategiesEnabled;
    }

    private static Map<String, String> goals() {
        Map<String, String> g = new LinkedHashMap<>();
        g.put("happy", "Exercise the primary happy path once for the selected feature.");
        g.put("empty", "Probe empty/cleared required fields and submit/continue.");
        g.put("boundary", "Probe max-length, special characters, or unicode in visible inputs.");
        g.put("abuse", "Try double-submit, repeat click, or Back after a success signal.");
        g.put("session", "If logged in, probe stale session / logout mid-flow; else skip via advance.");
        g.put("invent", "Prefer emitting candidate edge scenarios; still record bugs if found.");
        return Collections.unmodifiableMap(g);
    }
}
