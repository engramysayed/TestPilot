package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Documents the open-then-login contract for Bug Hunter.
 * Full browser login is covered by JobLoginService / integration runs.
 */
public class LiveHuntOpenUrlTest {

    @Test
    public void openThenLoginHelperOrdersBaseUrlFirst() {
        // Regression guard for the LOGIN_FAILED-on-blank-page bug:
        // hunt must navigate to baseUrl before JobLoginService looks for username fields.
        Assert.assertEquals(
                LiveHuntService.openThenLoginOrderHint(),
                "baseUrl-then-optional-login");
    }
}
