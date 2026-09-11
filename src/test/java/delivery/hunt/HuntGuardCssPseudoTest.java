package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class HuntGuardCssPseudoTest {

    private static HuntActionGuard guard() {
        String slim = "<body><button id='save'>Save</button></body>";
        return new HuntActionGuard(
                HuntPageMapBuilder.build("https://ex/app", "App", slim), slim);
    }

    @Test
    public void rejectsJqueryStylePseudoSelectors() {
        for (String locator : new String[]{
                "button:contains('Save')", "div:has(> button)", "li:eq(2)", "input:visible"}) {
            var reason = guard().rejectReason(Map.of(
                    "type", "click", "locatorStrategy", "css", "locator", locator));
            Assert.assertTrue(reason.isPresent(), "must reject " + locator);
            Assert.assertTrue(reason.get().contains("unsupported CSS pseudo-class"), reason.get());
        }
    }

    @Test
    public void allowsXpathContainsAndGroundedCss() {
        Assert.assertTrue(guard().rejectReason(Map.of(
                        "type", "click", "locatorStrategy", "css", "locator", "#save"))
                .isEmpty());
        var xpath = guard().rejectReason(Map.of(
                "type", "click", "locatorStrategy", "xpath",
                "locator", "//button[contains(normalize-space(.),'Save')]"));
        Assert.assertFalse(String.valueOf(xpath).contains("unsupported CSS pseudo-class"), String.valueOf(xpath));
    }
}
