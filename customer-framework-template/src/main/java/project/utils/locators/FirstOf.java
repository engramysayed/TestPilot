package project.utils.locators;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import project.utils.Logs.LogsManager;

import java.util.List;
import java.util.Map;

/**
 * Ordered fallback over plain Selenium locators (primary first). Resolves to the matches of the
 * <b>first</b> locator that finds anything — never a union — so waits and actions keep single-element
 * semantics. On every resolution, writes one {@code WIN} event for the winning slot and one {@code MISS}
 * event for each earlier slot that was tried-and-missed, so the portal's LocatorOutcomeStore can rank
 * slots for the next codegen run.
 */
public final class FirstOf extends By {
    private final String pageClass;
    private final String label;
    private final List<By> bys;
    private volatile int lastHit = -1;

    private FirstOf(String pageClass, String label, List<By> bys) {
        if (bys.isEmpty()) {
            throw new IllegalArgumentException("FirstOf needs at least one locator");
        }
        this.pageClass = pageClass == null ? "" : pageClass;
        this.label = label;
        this.bys = List.copyOf(bys);
    }

    /** New signature: codegen emits this so slot win/miss can be keyed per (page, field). */
    public static FirstOf of(String pageClass, String fieldName, By... bys) {
        return new FirstOf(pageClass, fieldName, List.of(bys));
    }

    /** Legacy signature: generated pages from earlier codegen runs stay compiling. */
    public static FirstOf of(String label, By... bys) {
        return new FirstOf("", label, List.of(bys));
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
                recordOutcome(i, "MISS");
                continue;
            }
            if (!found.isEmpty()) {
                record(i);
                return found;
            }
            recordOutcome(i, "MISS");
        }
        return List.of();
    }

    private void record(int i) {
        int previous = lastHit;
        lastHit = i;
        recordOutcome(i, "WIN");
        if (previous == i || i == 0) {
            return;
        }
        LogsManager.warn("locator " + label + ": fallback " + i + " won -> " + bys.get(i));
        try {
            project.utils.reports.KeelResults.append(project.utils.reports.KeelResults.LOCATOR_EVENTS,
                    java.util.Map.of("locator", label, "index", i, "target", String.valueOf(bys.get(i))));
        } catch (RuntimeException ignored) {
        }
    }

    private void recordOutcome(int slot, String outcome) {
        try {
            project.utils.reports.KeelResults.append(project.utils.reports.KeelResults.LOCATOR_OUTCOMES,
                    Map.of(
                            "page", pageClass,
                            "field", label,
                            "slot", slot,
                            "outcome", outcome,
                            "target", String.valueOf(bys.get(slot))));
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public String toString() {
        return "FirstOf(" + label + ")" + bys;
    }
}
