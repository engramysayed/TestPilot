package delivery.locator;

import delivery.authoring.GeneratedIdDetector;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class LocatorStabilityPolicyTest {

    @Test
    public void tiers() {
        Assert.assertEquals(LocatorStabilityPolicy.tier("css", "button[data-axis-test-id='x']", List.of("data-axis-test-id")),
                LocatorTier.PREFERRED_HOOK);
        Assert.assertEquals(LocatorStabilityPolicy.tier("css", "button[data-testid='save']", List.of()), LocatorTier.TEST_ID);
        Assert.assertEquals(LocatorStabilityPolicy.tier("id", "email", List.of()), LocatorTier.ID);
        Assert.assertEquals(LocatorStabilityPolicy.tier("id", "ember1423", List.of()), LocatorTier.BRITTLE);
        Assert.assertEquals(LocatorStabilityPolicy.tier("name", "email", List.of()), LocatorTier.NAME);
        Assert.assertEquals(LocatorStabilityPolicy.tier("css", "button[role='button'][aria-label='Save']", List.of()),
                LocatorTier.ROLE_NAME);
        Assert.assertEquals(LocatorStabilityPolicy.tier("css", "input[placeholder='Search']", List.of()), LocatorTier.LABEL);
        Assert.assertEquals(LocatorStabilityPolicy.tier("xpath", "//button[contains(normalize-space(.),'Save')]", List.of()),
                LocatorTier.TEXT);
        Assert.assertEquals(LocatorStabilityPolicy.tier("css", "div:nth-child(3)", List.of()), LocatorTier.ORDINAL);
        Assert.assertEquals(LocatorStabilityPolicy.tier("css", "button.css-1x2y3z4", List.of()), LocatorTier.BRITTLE);
        Assert.assertEquals(LocatorStabilityPolicy.tier("css", "div.Button_root__a8f3k", List.of()), LocatorTier.BRITTLE);
        Assert.assertEquals(LocatorStabilityPolicy.tier("keel", "frame=#f >> css=button[data-testid='pay']", List.of()),
                LocatorTier.TEST_ID);
    }

    @Test
    public void stableFirstOrder() {
        int hook = LocatorStabilityPolicy.rank(LocatorTier.PREFERRED_HOOK, LocatorMode.STABLE_FIRST);
        int tid = LocatorStabilityPolicy.rank(LocatorTier.TEST_ID, LocatorMode.STABLE_FIRST);
        int id = LocatorStabilityPolicy.rank(LocatorTier.ID, LocatorMode.STABLE_FIRST);
        int name = LocatorStabilityPolicy.rank(LocatorTier.NAME, LocatorMode.STABLE_FIRST);
        int role = LocatorStabilityPolicy.rank(LocatorTier.ROLE_NAME, LocatorMode.STABLE_FIRST);
        int text = LocatorStabilityPolicy.rank(LocatorTier.TEXT, LocatorMode.STABLE_FIRST);
        Assert.assertTrue(hook > tid && tid > id && id > name && name > role && role > text);
    }

    @Test
    public void userFacingOrderStillHookFirst() {
        Assert.assertTrue(LocatorStabilityPolicy.rank(LocatorTier.PREFERRED_HOOK, LocatorMode.USER_FACING_FIRST)
                > LocatorStabilityPolicy.rank(LocatorTier.ROLE_NAME, LocatorMode.USER_FACING_FIRST));
        Assert.assertTrue(LocatorStabilityPolicy.rank(LocatorTier.ROLE_NAME, LocatorMode.USER_FACING_FIRST)
                > LocatorStabilityPolicy.rank(LocatorTier.TEST_ID, LocatorMode.USER_FACING_FIRST));
    }

    @Test
    public void aspNetAndJsfNamesAreNotStable() {
        Assert.assertTrue(GeneratedIdDetector.looksGeneratedName("ctl00$MainContent$txtUser"));
        Assert.assertTrue(GeneratedIdDetector.looksGeneratedName("loginForm:j_idt12:email"));
        Assert.assertFalse(GeneratedIdDetector.looksGeneratedName("email"));
        Assert.assertEquals(GeneratedIdDetector.humanTailSegment("ctl00$MainContent$txtUser"), "txtUser");
        Assert.assertEquals(LocatorStabilityPolicy.tier("name", "ctl00$MainContent$txtUser", List.of()), LocatorTier.BRITTLE);
        Assert.assertTrue(GeneratedIdDetector.looksGenerated("ctl00_MainContent_txtUser"));
    }

    @Test
    public void generatedClassesDetectedHumanClassesKept() {
        Assert.assertTrue(GeneratedIdDetector.looksGeneratedClass("css-1x2y3z4"));
        Assert.assertTrue(GeneratedIdDetector.looksGeneratedClass("sc-bdVaJa"));
        Assert.assertTrue(GeneratedIdDetector.looksGeneratedClass("Button_root__a8f3k"));
        Assert.assertTrue(GeneratedIdDetector.looksGeneratedClass("jss123"));
        Assert.assertFalse(GeneratedIdDetector.looksGeneratedClass("btn-primary"));
        Assert.assertFalse(GeneratedIdDetector.looksGeneratedClass("col-md-6"));
        Assert.assertFalse(GeneratedIdDetector.looksGeneratedClass("myNavBar"));
        Assert.assertFalse(GeneratedIdDetector.looksGeneratedClass("MuiButton-root"));
    }
}
