package delivery.util;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;

public class ProjectNamingTest {
    @Test
    public void hostSlug_stripsWwwAndDots() {
        Assert.assertEquals(ProjectNaming.hostSlug("https://www.saucedemo.com/"), "saucedemo-com");
        Assert.assertEquals(ProjectNaming.hostSlug("https://saucedemo.com/inventory"), "saucedemo-com");
    }

    @Test
    public void fromBaseUrl_includesTimestamp() {
        Instant fixed = Instant.parse("2026-08-05T12:19:26Z");
        String name = ProjectNaming.fromBaseUrl("https://www.saucedemo.com/", fixed);
        Assert.assertTrue(name.startsWith("saucedemo-com-"));
        Assert.assertTrue(name.matches("saucedemo-com-\\d{8}-\\d{6}"));
    }
}
