package delivery.snapshot;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cache first, then AI: replay a cached locator when the route matches and it still resolves
 * uniquely; otherwise serialize the page, let the AI pick an element id ("eN"), rank locators for
 * that element, cache the top 3 and return the element.
 */
public final class LocatorPipeline {

    public enum Source { CACHE, AI }

    public record Located(WebElement element, RankedLocators ranked, Source source, ReplayCache.Outcome cacheOutcome) {
    }

    private final ReplayCache cache;
    private final List<String> preferredAttrs;

    public LocatorPipeline(ReplayCache cache, List<String> preferredAttrs) {
        this.cache = cache;
        this.preferredAttrs = preferredAttrs == null ? List.of() : List.copyOf(preferredAttrs);
    }

    public Optional<Located> locate(WebDriver driver, String step, List<String> keywords, Function<PageModel, String> aiPick) {
        String url = driver.getCurrentUrl();
        ReplayCache.Lookup hit = cache.lookup(driver, step, url);
        if (hit.outcome() == ReplayCache.Outcome.HIT)
            return Optional.of(new Located(hit.element().get(), hit.ranked().get(), Source.CACHE, hit.outcome()));
        KeelTargetResolver.reset(driver);
        PageModel model = PageSerializer.serialize(driver, keywords, preferredAttrs);
        Optional<SnapshotCandidate> chosen = model.byId(aiPick.apply(model));
        if (chosen.isEmpty()) return Optional.empty();
        String route = RouteNormalizer.normalize(url);
        Optional<RankedLocators> ranked = LocatorRanker.rank(driver, chosen.get(), preferredAttrs, route);
        if (ranked.isEmpty()) return Optional.empty();
        WebElement el = KeelTargetResolver.find(driver, ranked.get().main().target());
        cache.put(step, url, ranked.get());
        return Optional.of(new Located(el, ranked.get(), Source.AI, hit.outcome()));
    }

    /** Call when the action on a located element failed: drops the cache entry so the next run asks the AI. */
    public void reportFailure(WebDriver driver, String step) {
        cache.invalidate(step, driver.getCurrentUrl());
    }
}
