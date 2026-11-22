package project.utils.locators;

import org.openqa.selenium.By;
import project.utils.reports.KeelResults;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends one WIN / MISS line per {@link MobileFirstOf} slot to {@code locator-outcomes.jsonl} so the
 * portal can rank mobile locators the same way it does on web. One line per attempt — the portal's
 * compaction pass sums them into {wins, misses} rows later.
 */
public final class MobileLocatorOutcomes {

    public static final String FILE = "locator-outcomes.jsonl";

    private MobileLocatorOutcomes() {}

    public static void append(String screenClassName, String fieldName, int slot, By by, String outcome) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("page", screenClassName == null ? "" : screenClassName);
            row.put("field", fieldName == null ? "" : fieldName);
            row.put("slot", slot);
            row.put("target", by == null ? "" : by.toString());
            row.put("outcome", outcome == null ? "MISS" : outcome);
            KeelResults.append(FILE, row);
        } catch (RuntimeException ignored) {
            // Never let the outcome ledger break the run.
        }
    }
}
