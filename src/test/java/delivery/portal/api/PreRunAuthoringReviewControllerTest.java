package delivery.portal.api;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PreRunAuthoringReviewControllerTest {

    @Test
    public void normalizeProvider_nullDefaultsToKeel() {
        Assert.assertEquals(PreRunAuthoringReviewController.normalizeProvider(null), "keel");
    }

    @Test
    public void normalizeProvider_blankDefaultsToKeel() {
        Assert.assertEquals(PreRunAuthoringReviewController.normalizeProvider(""), "keel");
        Assert.assertEquals(PreRunAuthoringReviewController.normalizeProvider("   "), "keel");
    }

    @Test
    public void normalizeProvider_trimsAndLowercases() {
        Assert.assertEquals(PreRunAuthoringReviewController.normalizeProvider(" Keel "), "keel");
        Assert.assertEquals(PreRunAuthoringReviewController.normalizeProvider("CURSOR"), "cursor");
        Assert.assertEquals(PreRunAuthoringReviewController.normalizeProvider("Ollama"), "ollama");
    }
}
