package project.utils.locators;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import project.utils.Logs.LogsManager;

import java.util.List;

/**
 * Ordered fallback over plain Selenium locators (primary first). Resolves to the matches of the
 * <b>first</b> locator that finds anything — never a union — so waits and actions keep single-element
 * semantics. Logs once when a fallback (not the primary) starts winning, so brittle primaries show up
 * in the run log and the Keel results file.
 */
public final class FirstOf extends By {
    private final String label;
    private final List<By> bys;
    private volatile int lastHit = -1;

    private FirstOf(String label, List<By> bys) {
        if (bys.isEmpty()) {
            throw new IllegalArgumentException("FirstOf needs at least one locator");
        }
        this.label = label;
        this.bys = List.copyOf(bys);
    }

    public static FirstOf of(String label, By... bys) {
        return new FirstOf(label, List.of(bys));
    }

    public List<By> locators() {
        return bys;
    }

    /** Index (0 = primary) of the locator that resolved last; -1 before any hit. */
    public int lastHit() {
        return lastHit;
    }

    @Override
    public List<WebElement> findElements(SearchContext context) {
        for (int i = 0; i < bys.size(); i++) {
            List<WebElement> found;
            try {
                found = bys.get(i).findElements(context);
            } catch (RuntimeException e) {
                continue; // invalid selector on this page / stale context: try the next one
            }
            if (!found.isEmpty()) {
                record(i);
                return found;
            }
        }
        return List.of();
    }

    private void record(int i) {
        int previous = lastHit;
        lastHit = i;
        if (previous == i || i == 0) {
            return;
        }
        LogsManager.warn("locator " + label + ": fallback " + i + " won -> " + bys.get(i));
        try {
            project.utils.reports.KeelResults.append(project.utils.reports.KeelResults.LOCATOR_EVENTS,
                    java.util.Map.of("locator", label, "index", i, "target", String.valueOf(bys.get(i))));
        } catch (RuntimeException ignored) {
            // reporting must never break the test
        }
    }

    @Override
    public String toString() {
        return "FirstOf(" + label + ")" + bys;
    }
}
