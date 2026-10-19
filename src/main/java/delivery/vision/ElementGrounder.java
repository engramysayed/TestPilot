package delivery.vision;

import delivery.authoring.DomCandidate;
import delivery.authoring.LocatorCandidate;
import delivery.authoring.LocatorValidator;
import utils.LogsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ElementGrounder {

    private static final LocatorValidator VALIDATOR = new LocatorValidator();

    private ElementGrounder() {
    }

    public static Optional<String> ground(
            List<DomCandidate> table,
            VisualCandidate visual,
            GroundingBrowser browser,
            int imageW,
            int imageH,
            int viewW,
            int viewH) {
        return groundToHit(table, visual, browser, imageW, imageH, viewW, viewH, "")
                .map(GroundingHit::candidateId);
    }

    public static Optional<GroundingHit> groundToHit(
            List<DomCandidate> table,
            VisualCandidate visual,
            GroundingBrowser browser,
            int imageW,
            int imageH,
            int viewW,
            int viewH) {
        return groundToHit(table, visual, browser, imageW, imageH, viewW, viewH, "");
    }

    public static Optional<GroundingHit> groundToHit(
            List<DomCandidate> table,
            VisualCandidate visual,
            GroundingBrowser browser,
            int imageW,
            int imageH,
            int viewW,
            int viewH,
            String namedText) {
        CssPoint imageCenter = CoordinateMapper.center(visual.boundingBox());
        CssPoint css = CoordinateMapper.toCss(imageCenter, imageW, imageH, viewW, viewH);
        GroundedNode node = browser.elementFromPoint(css.x(), css.y(), namedText == null ? "" : namedText);
        if (node == null || !node.displayed() || !node.enabled()) {
            LogsManager.info("GROUNDING: miss id=");
            return Optional.empty();
        }
        // Existing candidates describe the observed control; their labels alone are not identity.
        // Honour configured hooks before stable fallbacks, and verify uniqueness against the live node.
        Optional<GroundingHit> hit = table.stream()
                .sorted(java.util.Comparator.comparingInt(c -> delivery.store.PreferredHooksStore.matches(
                        c.value(), delivery.store.PreferredHooksStore.current()) ? 0 : 1))
                .filter(c -> browser.hasObservationIdentity() || matches(c, node))
                .filter(c -> safe(node.preferredAttribute()).isBlank() || preferredMatches(c, node))
                .filter(c -> browser.matchesObservedNode(c.strategy(), c.value()))
                .findFirst().map(c -> new GroundingHit(c.id(), new ArrayList<>(table), false));
        if (hit.isEmpty()) hit = addOrMatch(table, node, visual);
        final Optional<GroundingHit> resolved = hit;
        if (hit.isPresent()) {
            var chosen = hit.get().table().stream().filter(c -> c.id().equals(resolved.get().candidateId())).findFirst();
            if (chosen.isEmpty() || !browser.matchesObservedNode(chosen.get().strategy(), chosen.get().value())) return Optional.empty();
        }
        if (hit.isPresent()) {
            String candidateId = hit.get().candidateId();
            LogsManager.info("GROUNDING: hit id=" + candidateId);
            hit.get().table().stream()
                    .filter(c -> candidateId.equals(c.id()))
                    .findFirst()
                    .ifPresent(c -> LogsManager.info(
                            "LOCATOR: strategy=" + c.strategy() + " value=" + c.value()));
        } else {
            LogsManager.info("GROUNDING: miss id=");
        }
        return hit;
    }

    public static Optional<GroundingHit> addOrMatch(
            List<DomCandidate> table, GroundedNode node, VisualCandidate visual) {
        List<DomCandidate> mutable = new ArrayList<>(table);
        mutable.sort(java.util.Comparator.comparingInt(c -> preferredMatches(c, node) ? 0 : 1));
        for (DomCandidate candidate : mutable) {
            if (matches(candidate, node)) {
                return Optional.of(new GroundingHit(candidate.id(), mutable, false));
            }
        }
        Optional<LocatorCandidate> locator = buildLocator(node);
        if (locator.isEmpty()) {
            return Optional.empty();
        }
        LocatorCandidate lc = locator.get();
        if (!VALIDATOR.validate(lc).valid()) {
            return Optional.empty();
        }
        String candidateId = nextVisionId(mutable);
        String label = labelFor(node, visual);
        mutable.add(new DomCandidate(candidateId, lc.strategy(), lc.value(), safe(node.role()).isBlank() ? node.tag() : node.role(), label));
        return Optional.of(new GroundingHit(candidateId, mutable, true));
    }

    private static boolean matches(DomCandidate candidate, GroundedNode node) {
        if (preferredMatches(candidate, node)) return true;
        if (!safe(node.preferredAttribute()).isBlank()) return false;
        String strategy = safe(candidate.strategy()).toLowerCase(Locale.ROOT);
        String value = safe(candidate.value());
        if ("id".equals(strategy) && !safe(node.id()).isBlank()) {
            return value.equals(node.id());
        }
        if (isDataTestStrategy(strategy) && !safe(node.dataTest()).isBlank()) {
            return strategy.equals(node.dataTestAttribute()) && value.equals(node.dataTest());
        }
        if (!safe(node.id()).isBlank() || !safe(node.dataTest()).isBlank()) return false;
        // Labels describe controls but are not identities; build and verify a new locator instead.
        return false;
    }

    static Optional<LocatorCandidate> buildLocator(GroundedNode node) {
        if (!safe(node.preferredAttribute()).isBlank() && !safe(node.preferredValue()).isBlank()) {
            return Optional.of(new LocatorCandidate("css", preferredCss(node), safe(node.tag()), ""));
        }
        if (!safe(node.id()).isBlank()) {
            return Optional.of(new LocatorCandidate("id", node.id(), safe(node.tag()), ""));
        }
        if (!safe(node.dataTest()).isBlank()) {
            return Optional.of(new LocatorCandidate(node.dataTestAttribute(), node.dataTest(), safe(node.tag()), ""));
        }
        if (!safe(node.name()).isBlank()) {
            return Optional.of(new LocatorCandidate("name", node.name(), safe(node.tag()), ""));
        }
        if (!safe(node.ariaLabel()).isBlank()) {
            String tag = safe(node.tag());
            if (tag.isBlank()) {
                tag = "div";
            }
            return Optional.of(new LocatorCandidate("css", cssAriaLabel(tag, node.ariaLabel()), tag, ""));
        }
        String text = safe(node.visibleText());
        if (!text.isBlank() && text.length() <= 80) {
            String tag = safe(node.tag());
            if (tag.isBlank()) {
                tag = "button";
            }
            String xpath = delivery.authoring.DomCandidateExtractor.innermostTextXpath(tag, text);
            return Optional.of(new LocatorCandidate("xpath", xpath, tag, text));
        }
        return Optional.empty();
    }

    static String cssAriaLabel(String tag, String ariaLabel) {
        if (ariaLabel.indexOf('\'') >= 0 && ariaLabel.indexOf('"') < 0) {
            return tag + "[aria-label=\"" + ariaLabel + "\"]";
        }
        if (ariaLabel.indexOf('"') >= 0) {
            return tag + "[aria-label='" + ariaLabel.replace("'", "\\'") + "']";
        }
        return tag + "[aria-label='" + ariaLabel + "']";
    }

    private static String preferredCss(GroundedNode node) {
        return safe(node.tag()) + "[" + node.preferredAttribute() + "='"
                + node.preferredValue().replace("\\", "\\\\").replace("'", "\\'") + "']";
    }

    private static boolean preferredMatches(DomCandidate candidate, GroundedNode node) {
        return !safe(node.preferredAttribute()).isBlank()
                && ("css".equalsIgnoreCase(candidate.strategy()) || "cssselector".equalsIgnoreCase(candidate.strategy()))
                && preferredCss(node).equals(candidate.value());
    }

    private static String labelFor(GroundedNode node, VisualCandidate visual) {
        if (!safe(node.ariaLabel()).isBlank()) {
            return truncate(safe(node.ariaLabel()));
        }
        if (!safe(node.visibleText()).isBlank()) {
            return truncate(safe(node.visibleText()));
        }
        return truncate(safe(visual.description()));
    }

    private static String truncate(String text) {
        return text.length() > 40 ? text.substring(0, 40) : text;
    }

    private static String nextVisionId(List<DomCandidate> table) {
        int max = 0;
        for (DomCandidate candidate : table) {
            String id = candidate.id();
            if (id != null && id.startsWith("cV")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(2)));
                } catch (NumberFormatException ignored) {
                    // skip non-numeric vision ids
                }
            }
        }
        return "cV" + (max + 1);
    }

    private static boolean isDataTestStrategy(String strategy) {
        return "data-testid".equals(strategy)
                || "data-test".equals(strategy)
                || "data-qa".equals(strategy)
                || "testid".equals(strategy);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
