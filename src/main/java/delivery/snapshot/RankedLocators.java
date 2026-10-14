package delivery.snapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Top-3 verified locators for one element: {@code main} (the proven one) plus up to 2 fallbacks,
 * scores descending, and an optional normalized route ("" when unknown).
 *
 * <p>IR shape ({@link #toIrJson()}) is compatible with Keel Codegen A2 (ProvenStepCodec):
 * locatorStrategy="keel", locatorValue=main, locatorCandidates=[main, fb1, fb2] with score;
 * "fallbacks" and "route" are additive fields the codec ignores.
 */
public record RankedLocators(ScoredLocator main, List<ScoredLocator> fallbacks, String route) {
    public static final int MAX_FALLBACKS = 2;

    public RankedLocators {
        if (main == null) throw new IllegalArgumentException("main locator required");
        fallbacks = List.copyOf(fallbacks == null ? List.of() : fallbacks.subList(0, Math.min(MAX_FALLBACKS, fallbacks.size())));
        route = route == null ? "" : route;
    }

    /** From verified candidates (any order): best score is main, next two are fallbacks. */
    public static RankedLocators of(List<ScoredLocator> verified, String route) {
        List<ScoredLocator> s = new ArrayList<>(verified);
        s.sort(Comparator.comparingDouble(ScoredLocator::score).reversed());
        return new RankedLocators(s.get(0), s.subList(1, s.size()), route);
    }

    public List<ScoredLocator> all() {
        List<ScoredLocator> out = new ArrayList<>();
        out.add(main);
        out.addAll(fallbacks);
        return out;
    }

    /** The locator that actually executed becomes main; the rest stay fallbacks by score. */
    public RankedLocators withProven(String provenValue) {
        List<ScoredLocator> rest = new ArrayList<>(all());
        ScoredLocator proven = rest.stream().filter(l -> l.value().equals(provenValue)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a ranked locator: " + provenValue));
        rest.remove(proven);
        rest.sort(Comparator.comparingDouble(ScoredLocator::score).reversed());
        return new RankedLocators(proven, rest, route);
    }

    public RankedLocators withRoute(String r) {
        return new RankedLocators(main, fallbacks, r);
    }

    public JSONObject toIrJson() {
        JSONArray cands = new JSONArray();
        all().forEach(l -> cands.put(l.toJson()));
        JSONArray fb = new JSONArray();
        fallbacks.forEach(l -> fb.put(l.toJson()));
        JSONObject o = new JSONObject()
                .put("locatorStrategy", main.strategy())
                .put("locatorValue", main.value())
                .put("locatorCandidates", cands)
                .put("fallbacks", fb);
        if (!route.isBlank()) o.put("route", route);
        int contract = all().stream().filter(l -> ScoredLocator.KEEL.equals(l.strategy()))
                .mapToInt(l -> KeelTarget.parse(l.value()).requiredVersion()).max().orElse(1);
        if (contract > 1) o.put("contract", contract); // absent == 1, so v1 IR stays byte-identical
        return o;
    }

    public static RankedLocators fromIrJson(JSONObject o) {
        JSONArray c = o.getJSONArray("locatorCandidates");
        List<ScoredLocator> all = new ArrayList<>();
        for (int i = 0; i < c.length(); i++) {
            JSONObject j = c.getJSONObject(i);
            all.add(new ScoredLocator(j.optString("strategy", ScoredLocator.KEEL), j.getString("value"),
                    j.optDouble("score", 0), ""));
        }
        String mainValue = o.optString("locatorValue", all.get(0).value());
        ScoredLocator main = all.stream().filter(l -> l.value().equals(mainValue)).findFirst().orElse(all.get(0));
        all.remove(main);
        return new RankedLocators(main, all, o.optString("route", ""));
    }
}
