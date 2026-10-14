package delivery.snapshot;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class RouteNormalizerTest {
    @Test public void stripsIdsQueryAndKeepsHashRoutes() {
        assertEquals(RouteNormalizer.normalize("https://Opssit.AxisPay.app/orders/12345?x=1"), "opssit.axispay.app/orders/:id");
        assertEquals(RouteNormalizer.normalize("https://h/p/prj_a81ea6ad1132/runs/550e8400-e29b-41d4-a716-446655440000/"), "h/p/:id/runs/:id");
        assertEquals(RouteNormalizer.normalize("https://h/app#/users/42/edit?tab=2"), "h/app#/users/:id/edit");
        assertEquals(RouteNormalizer.normalize("https://h/"), "h/");
        assertEquals(RouteNormalizer.normalize("https://h/dashboard/settings"), "h/dashboard/settings");
    }
    @Test public void stepNormalization() {
        assertEquals(ReplayCache.normalizeStep("  Click   the Save button. "), "click the save button");
    }
    @Test public void dynamicTokens() {
        assertTrue(LocatorRanker.looksDynamic("ember1234"));
        assertTrue(LocatorRanker.looksDynamic("css-1x2ab3"));
        assertTrue(LocatorRanker.looksDynamic(":r1:"));
        assertFalse(LocatorRanker.looksDynamic("del-1"));
        assertFalse(LocatorRanker.looksDynamic("primary"));
    }
}
