package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class KeelPathSuggesterTest {
    @Test
    public void suggestsManualForUpload() {
        ManualTestCase tc = new ManualTestCase(
                "TC_01", "Upload", "", "1. Choose file\n2. Attach resume", "Uploaded", "P2", "negative",
                "", "", "", "");
        Assert.assertEquals(KeelPathSuggester.suggest(tc).suggestedKeelPath(), "MANUAL");
    }

    @Test
    public void suggestsAutomateForLinearLogin() {
        ManualTestCase tc = new ManualTestCase(
                "TC_01", "Login", "", "1. Open the Login Page at /login\n2. Enter in the Email or phone field\n3. Click Log in",
                "Home", "P1", "smoke", "", "", "", "");
        Assert.assertEquals(KeelPathSuggester.suggest(tc).suggestedKeelPath(), "AUTOMATE");
    }

    @Test
    public void applyToBlank_fillsOnlyBlank() {
        ManualTestCase blank = new ManualTestCase(
                "TC_01", "Login", "", "1. Open the Login Page at /login\n2. Click Log in", "Ok", "P1", "",
                "", "", "", "");
        ManualTestCase set = new ManualTestCase(
                "TC_02", "X", "", "1. Click Save", "Ok", "P1", "", "", "", "VISION_ONLY", "");
        List<ManualTestCase> out = KeelPathSuggester.applyToBlank(List.of(blank, set));
        Assert.assertFalse(out.get(0).keelPath().isBlank());
        Assert.assertEquals(out.get(1).keelPath(), "VISION_ONLY");
    }
}
