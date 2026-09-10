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
    public void buttonTextXpathDoesNotRequireTheButtonToBeTheInnermostTextNode() {
        String html = "<div><button aria-hidden=\"false\">Place order</button></div>";
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        DomCandidate textCandidate = candidates.stream()
                .filter(c -> "xpath".equals(c.strategy()) && c.value().contains("Place order"))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(textCandidate, candidates.toString());
        Assert.assertEquals(
                textCandidate.value(),
                "//button[contains(normalize-space(.),'Place order')]",
                "a tagged button xpath already excludes the wrapper");
        Assert.assertFalse(textCandidate.value().contains("not(.//*"),
                "innermost-not excludes buttons whose label lives in a child span");
    }

    @Test
    public void verifyOtpButtonWithInnerSpanUsesSimpleButtonXpath() {
        String html = """
                <body>
                  <button data-axis-test-id="verify_Otp_Button" type="button">
                    <span>Verify OTP</span>
                  </button>
                </body>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        Assert.assertTrue(candidates.stream().anyMatch(c ->
                        "css".equals(c.strategy())
                                && c.value().contains("data-axis-test-id")
                                && c.value().contains("verify_Otp_Button")),
                "vendor data-*test* hooks must be candidates: "
                        + DomCandidateExtractor.formatTable(candidates));
        Assert.assertTrue(candidates.stream().noneMatch(c ->
                        c.value().contains("Verify OTP") && c.value().contains("not(.//*")),
                "inner-span buttons must not emit the innermost-not xpath: "
                        + DomCandidateExtractor.formatTable(candidates));
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
