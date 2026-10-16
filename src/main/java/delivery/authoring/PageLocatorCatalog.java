package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import utils.LogsManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Live-page locator catalog. Rebuilds only when the URL or a cheap DOM fingerprint
 * changes (navigation, submenu expand, modal). Bind reads this instead of
 * re-extracting every step. Vision may only pick from it, or add a node it saw
 * that the extractor missed.
 */
public final class PageLocatorCatalog {
    public static final int MAX = 400;

    private String fingerprint = "";
    private List<DomCandidate> candidates = List.of();
    private int generation;

    public List<DomCandidate> ensure(String url, String html) {
        String fp = fingerprint(url, html);
        if (fp.equals(fingerprint) && !candidates.isEmpty()) {
            return candidates;
        }
        candidates = List.copyOf(DomCandidateExtractor.extract(html, MAX));
        fingerprint = fp;
        generation++;
        LogsManager.info("PAGE_CATALOG rebuild gen=" + generation + " n=" + candidates.size()
                + " url=" + (url == null ? "" : url));
        return candidates;
    }

    public void clear() {
        fingerprint = "";
        candidates = List.of();
        generation = 0;
    }

    public int generation() {
        return generation;
    }

    public List<DomCandidate> candidates() {
        return candidates;
    }

    /**
     * If vision boxed a control we already catalogued, keep the stable locator.
     * If it found something new (DOM changed under us, or extractor gap), adopt it.
     */
    public DomCandidate adoptOrPreferStable(DomCandidate vision) {
        if (vision == null) {
            return null;
        }
        DomCandidate existing = findRelated(vision);
        if (existing != null) {
            DomCandidate stable = preferStable(existing);
            LogsManager.info("PAGE_CATALOG vision hit matched " + stable.strategy() + ":" + stable.value());
            return stable;
        }
        List<DomCandidate> next = new ArrayList<>(candidates);
        next.add(vision);
        candidates = List.copyOf(next);
        LogsManager.info("PAGE_CATALOG vision adopted " + vision.strategy() + ":" + vision.value());
        return vision;
    }

    public List<ProvenStep> stabilizeSteps(List<ProvenStep> steps, StepIntentBinder.IntentLine intent) {
        if (steps == null || steps.isEmpty()) {
            return steps;
        }
        String hint = intent == null ? "" : StepIntentBinder.quotedVisibleName(intent.text());
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep step : steps) {
            DomCandidate guess = new DomCandidate(
                    "v",
                    step.locatorStrategy() == null ? "" : step.locatorStrategy(),
                    step.locatorValue() == null ? "" : step.locatorValue(),
                    "",
                    hint == null || hint.isBlank() ? step.rationale() : hint);
            DomCandidate chosen = adoptOrPreferStable(guess);
            if (chosen == null || keysEqual(chosen, guess)) {
                out.add(step);
                continue;
            }
            out.add(new ProvenStep(
                    step.tcId(), step.pageName(), step.actionType(), step.action(),
                    chosen.strategy(), chosen.value(), step.value(),
                    step.assertionType(), step.assertionExpected(), step.validated(),
                    (step.rationale() + " [catalog " + chosen.strategy() + "]").trim(),
                    step.screenshotRelPath(), step.locatorCandidates(), step.dropCandidates(),
                    step.sideEffects(), step.secret()));
        }
        return out;
    }

    static String fingerprint(String url, String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        List<String> hooks = new ArrayList<>();
        for (Element el : doc.getAllElements()) {
            for (Attribute attr : el.attributes()) {
                if (DomCandidateExtractor.isTestHookAttr(attr.getKey())
                        && attr.getValue() != null && !attr.getValue().isBlank()) {
                    hooks.add(attr.getKey().toLowerCase(Locale.ROOT) + "=" + attr.getValue());
                }
            }
        }
        hooks.sort(String::compareTo);
        int interactive = doc.select(DomCandidateExtractor.INTERACTIVE_QUERY).size();
        String nav = doc.select("nav, aside, [role=navigation], [class*=sidebar], [class*=side-menu]").text();
        return (url == null ? "" : url.trim()) + "\n" + interactive + "\n"
                + String.join("|", hooks) + "\n" + Integer.toHexString(nav.hashCode());
    }

    private DomCandidate findRelated(DomCandidate vision) {
        if (vision == null) {
            return null;
        }
        String wantVal = norm(vision.value());
        String wantLabel = norm(vision.label());
        for (DomCandidate c : candidates) {
            if (keysEqual(c, vision)) {
                return c;
            }
            if (!wantVal.isBlank() && norm(c.value()).contains(wantVal.replace("xpath://", ""))) {
                return c;
            }
            if (!wantLabel.isBlank() && (wantLabel.equals(norm(c.label()))
                    || norm(c.value()).contains(wantLabel.replace(' ', '-')))) {
                return c;
            }
        }
        return null;
    }

    private DomCandidate preferStable(DomCandidate related) {
        String label = norm(related.label());
        return candidates.stream()
                .filter(c -> keysEqual(c, related) || (!label.isBlank() && label.equals(norm(c.label()))))
                .max(Comparator.comparingInt(c -> DomCandidateExtractor.strategyRank(c.strategy(), c.value())))
                .orElse(related);
    }

    private static boolean keysEqual(DomCandidate a, DomCandidate b) {
        return a != null && b != null
                && a.strategy().equalsIgnoreCase(b.strategy())
                && a.value().equals(b.value());
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
