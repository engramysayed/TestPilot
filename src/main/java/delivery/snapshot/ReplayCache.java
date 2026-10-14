package delivery.snapshot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Replay cache keyed by normalized step text + normalized route. Replays only when the route matches
 * and a cached locator (main first, then fallbacks) still resolves uniquely; otherwise the entry is
 * invalidated and the caller falls through to the AI.
 */
public final class ReplayCache {

    public enum Outcome { HIT, MISS, ROUTE_MISMATCH, STALE }

    public record Lookup(Outcome outcome, Optional<RankedLocators> ranked, Optional<ScoredLocator> used,
                         Optional<WebElement> element) {
        static Lookup of(Outcome o) {
            return new Lookup(o, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    private final Map<String, RankedLocators> entries = new LinkedHashMap<>();

    public static String normalizeStep(String step) {
        return step == null ? "" : step.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("[.!\\s]+$", "").trim();
    }

    static String key(String step, String route) {
        return normalizeStep(step) + "\u0000" + route;
    }

    public synchronized void put(String step, String url, RankedLocators ranked) {
        String route = RouteNormalizer.normalize(url);
        entries.put(key(step, route), ranked.withRoute(route));
    }

    public synchronized void invalidate(String step, String url) {
        entries.remove(key(step, RouteNormalizer.normalize(url)));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized Lookup lookup(WebDriver driver, String step, String url) {
        String route = RouteNormalizer.normalize(url);
        RankedLocators r = entries.get(key(step, route));
        if (r == null) {
            String prefix = normalizeStep(step) + "\u0000";
            boolean other = entries.keySet().stream().anyMatch(k -> k.startsWith(prefix));
            return Lookup.of(other ? Outcome.ROUTE_MISMATCH : Outcome.MISS);
        }
        for (ScoredLocator l : r.all()) {
            try {
                List<WebElement> m = KeelTargetResolver.findAll(driver, l.target());
                if (m != null && m.size() == 1) {
                    // Driver stays in the target's frame so the caller can act on the element.
                    return new Lookup(Outcome.HIT, Optional.of(r), Optional.of(l), Optional.of(m.get(0)));
                }
            } catch (UnsupportedOperationException firefox) {
                throw firefox;
            } catch (RuntimeException ignored) {
                // try next fallback
            }
            KeelTargetResolver.reset(driver);
        }
        entries.remove(key(step, route));
        return Lookup.of(Outcome.STALE);
    }

    public synchronized void save(Path file) throws IOException {
        JSONArray arr = new JSONArray();
        entries.forEach((k, v) -> arr.put(new JSONObject().put("step", k.substring(0, k.indexOf('\u0000')))
                .put("entry", v.toIrJson())));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, new JSONObject().put("version", 1).put("entries", arr).toString(2), StandardCharsets.UTF_8);
    }

    public static ReplayCache load(Path file) throws IOException {
        ReplayCache c = new ReplayCache();
        if (!Files.exists(file)) return c;
        JSONArray arr = new JSONObject(Files.readString(file, StandardCharsets.UTF_8)).optJSONArray("entries");
        if (arr == null) return c;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            RankedLocators r = RankedLocators.fromIrJson(o.getJSONObject("entry"));
            c.entries.put(key(o.getString("step"), r.route()), r);
        }
        return c;
    }
}
