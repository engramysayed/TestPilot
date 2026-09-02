package delivery.vision;

import java.util.List;
import java.util.stream.Collectors;

public final class VisionHealHints {

    private VisionHealHints() {
    }

    public static String ollamaSection() {
        List<String> lines = VisionAttemptLog.linesForHeal();
        if (lines.isEmpty()) {
            return "";
        }
        return "\n## Vision attempts this intent\n"
                + lines.stream().map(s -> "- " + s).collect(Collectors.joining("\n"))
                + "\n";
    }
}
