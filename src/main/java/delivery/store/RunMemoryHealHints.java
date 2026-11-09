package delivery.store;

import java.util.List;
import java.util.stream.Collectors;

public final class RunMemoryHealHints {

    private RunMemoryHealHints() {
    }

    public static String ollamaSection() {
        List<String> lines = RunMemoryHintsStore.currentLines();
        if (lines.isEmpty()) {
            return "";
        }
        return "\n## Approved run memory (prefer these locators when tied)\n"
                + lines.stream().map(s -> "- " + s).collect(Collectors.joining("\n"))
                + "\n";
    }
}
