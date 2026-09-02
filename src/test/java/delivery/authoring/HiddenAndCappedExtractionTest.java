package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class HiddenAndCappedExtractionTest {

    @Test
    public void hiddenTwinOfAControlIsNotOffered() {
        String html = """
                <nav style="display:none"><button id="menu-toggle">Menu</button></nav>
                <nav><button id="menu-toggle-desktop">Menu</button></nav>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        Assert.assertTrue(candidates.stream().noneMatch(c -> "menu-toggle".equals(c.value())),
                "a control inside a display:none container is unreachable: " + candidates);
        Assert.assertTrue(candidates.stream().anyMatch(c -> "menu-toggle-desktop".equals(c.value())),
                candidates.toString());
    }

    @Test
    public void ariaHiddenAndTemplateContentAreSkipped() {
        String html = """
                <div aria-hidden="true"><button id="offscreen-help">Help</button></div>
                <template><button id="row-template-delete">Delete</button></template>
                <input type="hidden" id="csrf-token" value="x">
                <button id="visible-help">Help</button>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        List<String> values = candidates.stream().map(DomCandidate::value).toList();

        Assert.assertFalse(values.contains("offscreen-help"), values.toString());
        Assert.assertFalse(values.contains("row-template-delete"), values.toString());
        Assert.assertFalse(values.contains("csrf-token"), values.toString());
        Assert.assertTrue(values.contains("visible-help"), values.toString());
    }

    @Test
    public void textXpathTargetsTheInnermostNode() {
        String html = "<div><button aria-hidden=\"false\">Place order</button></div>";
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        DomCandidate textCandidate = candidates.stream()
                .filter(c -> "xpath".equals(c.strategy()) && c.value().contains("Place order"))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(textCandidate, candidates.toString());
        Assert.assertTrue(textCandidate.value().contains("not(.//*[contains("),
                "an ancestor would otherwise win: " + textCandidate.value());
    }

    @Test
    public void textScopedXpathIsAcceptedByTheValidator() {
        LocatorValidator validator = new LocatorValidator();
        String xpath = DomCandidateExtractor.innermostTextXpath("button", "Place order");

        Assert.assertTrue(validator.validate(
                        new LocatorCandidate("xpath", xpath, "Page", "")).valid(),
                "a label-only button must be usable: " + xpath);
    }

    @Test
    public void fallbackCandidatesSurviveAPageFullOfIds() {
        StringBuilder html = new StringBuilder("<body>");
        for (int i = 0; i < 200; i++) {
            html.append("<div id=\"section-block-").append(i).append("\">text</div>");
        }
        html.append("<button aria-label=\"Place order\">Go</button>");
        html.append("<input aria-label=\"Coupon code\" type=\"text\">");
        html.append("</body>");

        List<DomCandidate> candidates = DomCandidateExtractor.extract(html.toString());

        Assert.assertTrue(candidates.stream().anyMatch(c -> c.value().contains("Place order")),
                "id-heavy pages must still expose their controls");
        Assert.assertTrue(candidates.stream().anyMatch(c -> c.value().contains("Coupon code")),
                "id-heavy pages must still expose their controls");
    }
}
