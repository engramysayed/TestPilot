package delivery.vision;

import delivery.authoring.DomCandidate;
import delivery.authoring.LocatorCandidate;
import delivery.authoring.LocatorValidator;
import delivery.authoring.XpathLiterals;
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
        return groundToHit(table, visual, browser, imageW, imageH, viewW, viewH)
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
        CssPoint imageCenter = CoordinateMapper.center(visual.boundingBox());
        CssPoint css = CoordinateMapper.toCss(imageCenter, imageW, imageH, viewW, viewH);
        GroundedNode node = browser.elementFromPoint(css.x(), css.y());
        if (node == null || !node.displayed() || !node.enabled()) {
            LogsManager.info("GROUNDING: miss id=");
            return Optional.empty();
        }
        Optional<GroundingHit> hit = addOrMatch(table, node, visual);
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
        mutable.add(new DomCandidate(candidateId, lc.strategy(), lc.value(), node.tag(), label));
        return Optional.of(new GroundingHit(candidateId, mutable, true));
    }

    private static boolean matches(DomCandidate candidate, GroundedNode node) {
        String strategy = safe(candidate.strategy()).toLowerCase(Locale.ROOT);
        String value = safe(candidate.value());
        if ("id".equals(strategy) && !safe(node.id()).isBlank() && value.equals(node.id())) {
            return true;
        }
        if (isDataTestStrategy(strategy) && !safe(node.dataTest()).isBlank() && value.equals(node.dataTest())) {
            return true;
        }
        if (("css".equals(strategy) || "cssselector".equals(strategy) || "xpath".equals(strategy))
                && !safe(node.dataTest()).isBlank()
                && value.contains(node.dataTest())) {
            return true;
        }
        boolean ariaMatch = !safe(node.ariaLabel()).isBlank()
                && safe(candidate.tag()).equalsIgnoreCase(safe(node.tag()))
                && safe(candidate.label()).equals(node.ariaLabel());
        return ariaMatch || sharesVisibleText(candidate, node);
    }

    private static boolean sharesVisibleText(DomCandidate candidate, GroundedNode node) {
        String text = safe(node.visibleText());
        if (text.isBlank()) {
            return false;
        }
        String label = safe(candidate.label());
        return !label.isBlank() && label.equalsIgnoreCase(text);
    }

    private static Optional<LocatorCandidate> buildLocator(GroundedNode node) {
        if (!safe(node.id()).isBlank()) {
            return Optional.of(new LocatorCandidate("id", node.id(), safe(node.tag()), ""));
        }
        if (!safe(node.dataTest()).isBlank()) {
            return Optional.of(new LocatorCandidate("data-test", node.dataTest(), safe(node.tag()), ""));
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
        if (!text.isBlank() && !text.contains("'") && text.length() <= 80) {
            String tag = safe(node.tag());
            if (tag.isBlank()) {
                tag = "button";
            }
            String xpath = "//" + tag + "[contains(normalize-space(.)," + XpathLiterals.quote(text) + ")]";
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
