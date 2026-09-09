package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class HuntActionGuardTest {

    private static final String SLIM = "<body><button id='real'>Go</button></body>";

    private HuntActionGuard guard() {
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/", "t", SLIM);
        return new HuntActionGuard(map, SLIM);
    }

    @Test
    public void rejectsInventedLocator() {
        HuntActionGuard g = guard();
        Assert.assertTrue(g.rejectReason(Map.of("type", "click", "locator", "#nope")).isPresent());
        Assert.assertTrue(g.rejectReason(Map.of(
                "type", "click", "locatorStrategy", "id", "locatorValue", "real")).isEmpty());
    }

    @Test
    public void rejectsInventedLocatorForAllGroundedTypes() {
        HuntActionGuard g = guard();
        for (String type : new String[] {"type", "clear", "assert_visible"}) {
            Assert.assertTrue(
                    g.rejectReason(Map.of("type", type, "locator", "#nope")).isPresent(),
                    "expected reject for type=" + type);
        }
    }

    @Test
    public void rejectsInventedCssWithoutMapMatch() {
        HuntActionGuard g = guard();
        Assert.assertTrue(g.rejectReason(Map.of("type", "click", "locator", "button.fake")).isPresent());
    }

    @Test
    public void rejectsSubstringMapKeyFalsePositive() {
        HuntActionGuard g = guard();
        Assert.assertTrue(g.rejectReason(Map.of("type", "click", "locator", "prefix id:real suffix")).isPresent());
    }

    @Test
    public void allowsNavigateWaitAndAssertText() {
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/", "t", "<body></body>");
        HuntActionGuard g = new HuntActionGuard(map, "<body></body>");
        Assert.assertTrue(g.rejectReason(Map.of("type", "navigate", "url", "https://ex/")).isEmpty());
        Assert.assertTrue(g.rejectReason(Map.of("type", "wait", "ms", 100)).isEmpty());
        Assert.assertTrue(g.rejectReason(Map.of("type", "assert_text", "text", "Hello")).isEmpty());
    }

    @Test
    public void allowsGroundedHashIdLocator() {
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/", "t",
                "<body><button id='real'>Go</button></body>");
        HuntActionGuard g = new HuntActionGuard(map, "<body><button id='real'>Go</button></body>");
        Assert.assertTrue(g.rejectReason(Map.of("type", "click", "locator", "#real")).isEmpty());
    }

    @Test
    public void executorRejectsUngroundedLocator() {
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/", "t",
                "<body><button id='real'>Go</button></body>");
        HuntActionExecutor ex = new HuntActionExecutor(null);
        ex.setGuard(new HuntActionGuard(map, "<body><button id='real'>Go</button></body>"));
        Map<String, Object> row = ex.executeOne(Map.of("type", "click", "locator", "#nope"));
        Assert.assertEquals(row.get("status"), "rejected");
        Assert.assertTrue(String.valueOf(row.get("reason")).startsWith("ungrounded_locator"));
    }
}
