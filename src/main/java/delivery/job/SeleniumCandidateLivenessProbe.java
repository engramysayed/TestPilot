package delivery.job;

import delivery.authoring.DomCandidate;
import delivery.heal.CandidateLiveness;
import delivery.heal.CandidateLivenessProbe;
import drivers.WebDriverFactory;
import executionLayer.SelectorParser;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;

/**
 * Probes shortlist candidates with Selenium. Misses and errors are treated as non-interactable
 * so heal never retries a locator the driver cannot use.
 */
public final class SeleniumCandidateLivenessProbe implements CandidateLivenessProbe {
    private final WebDriverFactory driverFactory;

    public SeleniumCandidateLivenessProbe(WebDriverFactory driverFactory) {
        this.driverFactory = driverFactory;
    }

    @Override
    public CandidateLiveness probe(DomCandidate candidate) {
        if (candidate == null || driverFactory == null || driverFactory.get() == null) {
            return CandidateLiveness.dead();
        }
        try {
            String strategy = candidate.strategy() == null ? "" : candidate.strategy().trim().toLowerCase();
            String value = candidate.value() == null ? "" : candidate.value();
            if (value.isBlank()) {
                return CandidateLiveness.dead();
            }
            By by = SelectorParser.toBy(normalize(strategy) + ":" + value);
            ContextSearch.Hit hit = ContextSearch.find(driverFactory, by);
            if (hit == null || hit.element() == null) {
                return CandidateLiveness.dead();
            }
            WebElement el = hit.element();
            Dimension size = el.getSize();
            int w = size == null ? 0 : size.getWidth();
            int h = size == null ? 0 : size.getHeight();
            return new CandidateLiveness(el.isDisplayed(), el.isEnabled(), w, h);
        } catch (RuntimeException e) {
            return CandidateLiveness.dead();
        }
    }

    private static String normalize(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return "css";
        }
        if ("cssselector".equals(strategy) || "css".equals(strategy)) {
            return "css";
        }
        return strategy;
    }
}
