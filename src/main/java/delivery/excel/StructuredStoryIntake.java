package delivery.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a prompt block from structured story-intake JSON (Generate enhancement 11).
 */
public final class StructuredStoryIntake {
    private StructuredStoryIntake() {
    }

    public record Intake(
            String title,
            List<String> acceptanceCriteria,
            String role,
            String surface,
            String freeText,
            String persona,
            String goal,
            String constraints,
            List<String> techniques,
            List<String> dataRefs
    ) {
        public Intake {
            acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
            techniques = techniques == null ? List.of() : List.copyOf(techniques);
            dataRefs = dataRefs == null ? List.of() : List.copyOf(dataRefs);
            title = title == null ? "" : title.trim();
            role = role == null ? "" : role.trim();
            surface = surface == null ? "" : surface.trim();
            freeText = freeText == null ? "" : freeText.trim();
            persona = persona == null ? "" : persona.trim();
            goal = goal == null ? "" : goal.trim();
            constraints = constraints == null ? "" : constraints.trim();
        }
    }

    public static Intake fromMap(Map<String, Object> body) {
        if (body == null) {
            body = Map.of();
        }
        return new Intake(
                str(body.get("title")),
                strList(body.get("acceptanceCriteria")),
                str(body.get("role")),
                str(body.get("surface")),
                str(body.get("freeText")),
                str(body.get("persona")),
                str(body.get("goal")),
                str(body.get("constraints")),
                strList(body.get("techniques")),
                strList(body.get("dataRefs"))
        );
    }

    /**
     * @param structuredMode when true, reject shallow free-text-only payloads
     */
    public static void validate(Intake intake, boolean structuredMode) {
        if (intake == null) {
            throw new IllegalArgumentException("Story intake body is required");
        }
        boolean hasStructured = !intake.title().isBlank()
                || !intake.acceptanceCriteria().isEmpty()
                || !intake.role().isBlank()
                || !intake.persona().isBlank()
                || !intake.goal().isBlank()
                || !intake.constraints().isBlank()
                || !intake.techniques().isEmpty()
                || !intake.dataRefs().isEmpty()
                || !intake.surface().isBlank();
        boolean hasFree = !intake.freeText().isBlank();
        if (!hasStructured && !hasFree) {
            throw new IllegalArgumentException("Story intake requires structured fields and/or freeText");
        }
        if (structuredMode && !hasStructured && hasFree) {
            if (intake.freeText().length() < 40 || !looksLikeRequirements(intake.freeText())) {
                throw new IllegalArgumentException(
                        "SHALLOW_STORY: structured mode rejects free-text-only intake without clear "
                                + "acceptance criteria — add title, acceptanceCriteria[], goal, or persona");
            }
        }
    }

    public static String toPromptBlock(Intake intake) {
        validate(intake, false);
        StringBuilder sb = new StringBuilder();
        append(sb, "Title", intake.title());
        append(sb, "Role", firstNonBlank(intake.role(), intake.persona()));
        append(sb, "Persona", intake.persona());
        append(sb, "Surface", intake.surface());
        append(sb, "Goal", intake.goal());
        append(sb, "Constraints", intake.constraints());
        if (!intake.acceptanceCriteria().isEmpty()) {
            sb.append("Acceptance criteria:\n");
            int i = 1;
            for (String ac : intake.acceptanceCriteria()) {
                if (ac == null || ac.isBlank()) continue;
                sb.append(i++).append(". ").append(ac.trim()).append('\n');
            }
            sb.append('\n');
        }
        if (!intake.techniques().isEmpty()) {
            sb.append("Requested techniques: ").append(String.join(", ", intake.techniques())).append("\n\n");
        }
        if (!intake.dataRefs().isEmpty()) {
            sb.append("Data refs: ").append(String.join(", ", intake.dataRefs())).append("\n\n");
        }
        if (!intake.freeText().isBlank()) {
            sb.append("Story / free text:\n").append(intake.freeText().trim()).append('\n');
        }
        return sb.toString().trim();
    }

    public static Map<String, Object> describe(Intake intake) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", intake.title());
        m.put("acceptanceCriteria", intake.acceptanceCriteria());
        m.put("role", intake.role());
        m.put("surface", intake.surface());
        m.put("freeText", intake.freeText());
        m.put("persona", intake.persona());
        m.put("goal", intake.goal());
        m.put("constraints", intake.constraints());
        m.put("techniques", intake.techniques());
        m.put("dataRefs", intake.dataRefs());
        return m;
    }

    static boolean looksLikeRequirements(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("given ") || lower.contains("when ") || lower.contains("then ")
                || lower.contains("as a ") || lower.contains("i want") || lower.contains("acceptance")
                || lower.contains("should ") || lower.contains("must ");
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(label).append(": ").append(value.trim()).append('\n');
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b == null ? "" : b;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        if (o == null) return List.of();
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                String s = String.valueOf(item).trim();
                if (!s.isBlank()) out.add(s);
            }
            return out;
        }
        String s = String.valueOf(o).trim();
        if (s.isBlank()) return List.of();
        if (s.contains("\n")) {
            List<String> out = new ArrayList<>();
            for (String line : s.split("\n")) {
                String t = line.trim();
                if (!t.isBlank()) out.add(t);
            }
            return out;
        }
        return List.of(s);
    }
}
