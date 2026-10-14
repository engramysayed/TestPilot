package delivery.mobile.runner.grammar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses LocatorOverrides sheet rows: TC_ID, Step, Context, Strategy, Value.
 * Duplicate (TC_ID, Step) conflicts. Step identity is frozen — reordering text does not rebind.
 */
public final class LocatorOverrideSheetParser {
    public record ParseResult(List<LocatorOverride> overrides, List<String> errors) {}

    public ParseResult parse(String sheetText) {
        List<LocatorOverride> out = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (sheetText == null || sheetText.isBlank()) return new ParseResult(List.of(), List.of());
        String[] lines = sheetText.split("\\R");
        boolean headerSkipped = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!headerSkipped && line.toUpperCase().startsWith("TC_ID")) {
                headerSkipped = true;
                continue;
            }
            headerSkipped = true;
            List<String> cols = splitCsv(line);
            if (cols.size() < 5) {
                errors.add("OVERRIDE_COLS: need TC_ID,Step,Context,Strategy,Value — got " + line);
                continue;
            }
            try {
                LocatorOverride o = new LocatorOverride(cols.get(0), cols.get(1), cols.get(2), cols.get(3), cols.get(4));
                if (!seen.add(o.key())) {
                    errors.add("OVERRIDE_DUPLICATE: " + o.tcId() + "/" + o.stepId());
                    continue;
                }
                out.add(o);
            } catch (GrammarException e) {
                errors.add(e.code() + ": " + e.getMessage());
            }
        }
        return new ParseResult(List.copyOf(out), List.copyOf(errors));
    }

    /** CSV with optional quotes; commas inside quotes preserved. */
    static List<String> splitCsv(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    inQuote = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuote = true;
            } else if (c == ',') {
                cols.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString().trim());
        return cols;
    }
}
