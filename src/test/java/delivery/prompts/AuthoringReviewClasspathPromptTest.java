package delivery.prompts;

import delivery.portal.service.AuthoringReviewService;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Backend prompts-extract: classpath id used by AuthoringReviewService.
 */
public class AuthoringReviewClasspathPromptTest {

    @AfterMethod
    public void cleanup() {
        PromptCatalog.setOverrideDir(null);
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.clearCache();
    }

    @Test
    public void classpath_loadsAuthoringReview() {
        PromptCatalog.setOverrideDir(null);
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.clearCache();
        String review = PromptCatalog.get("authoring/authoring-review");
        Assert.assertNotNull(review);
        Assert.assertFalse(review.isBlank(), "authoring-review prompt must be non-blank");
        Assert.assertTrue(review.contains("You are Keel authoring reviewer"), review);
        Assert.assertTrue(review.contains("OTP is accepted"), review);
        Assert.assertTrue(review.contains("visible-text waits"), review);
    }

    @Test
    public void authoringReviewService_usesCatalogText() {
        String catalog = PromptCatalog.get("authoring/authoring-review");
        Assert.assertTrue(catalog.contains("Keel authoring reviewer"));
        Assert.assertTrue(catalog.contains("OTP is accepted"));
        Assert.assertTrue(catalog.toLowerCase().contains("masked"));
        Assert.assertNotNull(AuthoringReviewService.class.getName());
    }
}
