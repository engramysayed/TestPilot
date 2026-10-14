package delivery.job;

import delivery.codegen.ProvenStep;
import executionLayer.SelectorParser;
import org.openqa.selenium.*;
import java.util.*;

/** Snapshot identity, not whole-HTML equality. Never executes a stale remaining action. */
final class PrecisionPageGuard {
    private final WebDriver driver;
    private final String url;
    private final WebElement document;
    private final Map<ProvenStep, WebElement> targets = new HashMap<>();
    private final Map<ProvenStep, String> signatures = new HashMap<>();

    PrecisionPageGuard(WebDriver driver, List<List<ProvenStep>> rows) {
        this.driver = driver;
        url = driver.getCurrentUrl();
        document = driver.findElement(By.tagName("html"));
        for (var row : rows) for (var step : row) {
            var matches = driver.findElements(by(step));
            if (matches.size() == 1) {
                targets.put(step, matches.get(0));
                signatures.put(step, signature(matches.get(0)));
            }
        }
    }

    boolean valid(List<ProvenStep> steps) {
        try {
            if (!url.equals(driver.getCurrentUrl()) || !document.equals(driver.findElement(By.tagName("html")))) return false;
            for (var step : steps) {
                var matches = driver.findElements(by(step));
                if (matches.size() != 1 || !matches.get(0).equals(targets.get(step))
                        || !signature(matches.get(0)).equals(signatures.get(step))) return false;
                // Enabled state may change after typing. The executor owns its normal wait.
                if (!"assert".equalsIgnoreCase(step.action()) && !matches.get(0).isDisplayed()) return false;
            }
            return true;
        } catch (StaleElementReferenceException | org.openqa.selenium.NoSuchElementException e) { return false; }
    }

    private static By by(ProvenStep step) {
        String strategy = step.locatorStrategy();
        if ("cssSelector".equalsIgnoreCase(strategy)) strategy = "css";
        return SelectorParser.toBy(strategy + ":" + step.locatorValue());
    }

    private static String signature(WebElement element) {
        String tag = element.getTagName();
        return tag + "|" + element.getDomAttribute("id") + "|" + element.getDomAttribute("name")
                + "|" + element.getDomAttribute("type") + "|" + element.getDomAttribute("href")
                + "|" + element.getDomAttribute("aria-label")
                + "|" + ("input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag) ? "" : element.getText());
    }
}
