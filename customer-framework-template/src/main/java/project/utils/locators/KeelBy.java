package project.utils.locators;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import io.qameta.allure.Allure;
import project.utils.Logs.LogsManager;

import java.util.List;

/**
 * Ordered fallback over up to 3 Keel targets (best first), as a {@link By} so every existing
 * framework helper and wait accepts it. Each lookup is {@link KeelLocate#find(WebDriver, List)}: from
 * defaultContent, replaying frame hops, exactly one match per hop. A target that does not resolve
 * (no/ambiguous match, missing frame, hook-missing JS error) falls through to the next one.
 * Contract errors are NOT swallowed: targets are parsed eagerly (unknown hop = hard error at class
 * init), and the Firefox shadow-closed {@link UnsupportedOperationException} propagates.
 * The driver is left in the winning target's frame; generated code calls {@link KeelLocate#reset}.
 */
public final class KeelBy extends By {
    private final String label;
    private final List<String> targets;
    private final List<List<KeelLocate.Hop>> parsed;
    private volatile int lastHit = -1;

    private KeelBy(String label, List<String> targets) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("KeelBy needs at least one target");
        }
        this.label = label;
        this.targets = List.copyOf(targets);
        this.parsed = this.targets.stream().map(KeelLocate::parse).toList();
    }

    public static KeelBy of(String label, String... targets) {
        return new KeelBy(label, List.of(targets));
    }

    public List<String> targets() {
        return targets;
    }

    /** True when any target carries a v2 provider=/point= modifier (click at an offset). */
    public boolean hasPointer() {
        return parsed.stream().anyMatch(KeelLocate::hasPointer);
    }

    /** Highest contract version among the targets. */
    public int requiredVersion() {
        return parsed.stream().mapToInt(KeelLocate::requiredVersion).max().orElse(1);
    }

    /**
     * Resolves (with fallbacks) and returns the anchor plus the offset from its centre for the
     * winning target's provider=/point= modifier; (0,0) for plain targets. Leaves the driver in the
     * anchor's frame.
     */
    public KeelLocate.ClickPoint clickPoint(WebDriver driver) {
        List<WebElement> found = findElements(driver);
        if (found.isEmpty()) {
            throw new org.openqa.selenium.NoSuchElementException("keel: no target resolved for " + this);
        }
        return KeelLocate.clickPointFor(driver, found.get(0), parsed.get(lastHit));
    }

    /** Context (frame/shadow) hops of the target that won last. */
    List<KeelLocate.Hop> winningContext() {
        List<KeelLocate.Hop> h = parsed.get(Math.max(lastHit, 0));
        return h.subList(0, KeelLocate.locatorIndex(h));
    }

    /** Rank (0-based) of the target that resolved last; -1 before any hit. */
    public int lastHit() {
        return lastHit;
    }

    @Override
    public List<WebElement> findElements(SearchContext context) {
        if (!(context instanceof WebDriver driver)) {
            throw new IllegalArgumentException("KeelBy resolves from the driver (top document), not " + context);
        }
        for (int i = 0; i < parsed.size(); i++) {
            try {
                WebElement el = KeelLocate.find(driver, parsed.get(i));
                recordHit(i);
                return List.of(el);
            } catch (NoSuchElementException | NoSuchFrameException | StaleElementReferenceException
                     | JavascriptException e) {
                // next ranked target
            }
        }
        driver.switchTo().defaultContent();
        return List.of();
    }

    /**
     * Logs (and adds an Allure step) when the winning index changes, so polling waits that call
     * findElements repeatedly do not flood the log. Index 0 = main locator, 1..2 = fallbacks.
     */
    private void recordHit(int i) {
        int previous = lastHit;
        lastHit = i;
        if (previous == i) {
            return;
        }
        String msg = "keel locator " + label + ": " + (i == 0 ? "main" : "fallback") + " index " + i
                + " won -> " + targets.get(i);
        if (i > 0) {
            LogsManager.warn(msg);
            project.utils.reports.KeelResults.append(project.utils.reports.KeelResults.LOCATOR_EVENTS,
                    java.util.Map.of("locator", label, "index", i, "target", targets.get(i)));
        } else {
            LogsManager.info(msg);
        }
        try {
            Allure.step(msg);
        } catch (RuntimeException | LinkageError noAllureContext) {
            // reporting must never break element lookup
        }
    }

    @Override
    public String toString() {
        return "KeelBy[" + label + "]" + targets;
    }
}
