package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ExcelPathNavigatorTest {

    @Test
    public void allowsOnlyTheExcelOpenPath() {
        Assert.assertTrue(ExcelPathNavigator.isAllowed("/reg/", "/reg/"));
        Assert.assertTrue(ExcelPathNavigator.isAllowed("/reg", "/reg/"));
        Assert.assertTrue(ExcelPathNavigator.isAllowed(
                "https://web.example.com/reg/", "/reg/"));
        Assert.assertFalse(ExcelPathNavigator.isAllowed("https://evil.example/phish", "/reg/"));
        Assert.assertFalse(ExcelPathNavigator.isAllowed("/login/", "/reg/"));
        Assert.assertFalse(ExcelPathNavigator.isAllowed("/reg/", null));
    }

    @Test
    public void resolvesRelativeToCurrentOrigin() {
        Assert.assertEquals(
                ExcelPathNavigator.resolve("https://web.example.com/login/?x=1", "/reg/"),
                "https://web.example.com/reg/");
    }
}
