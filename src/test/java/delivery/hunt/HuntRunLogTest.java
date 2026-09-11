package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class HuntRunLogTest {

    @Test
    public void actionLogDoesNotThrowForCommonRows() {
        HuntRunLog.info("unit-test probe");
        HuntRunLog.cycleStart(1, 10, "https://example.test/login", "mode=happy");
        HuntRunLog.planner(1, "continue", 3, "try happy path");
        HuntRunLog.action(Map.of(
                "type", "type",
                "status", "ok",
                "locator", "[data-axis-test-id='username_Input']",
                "value", "u"));
        HuntRunLog.action(Map.of(
                "type", "click",
                "status", "rejected",
                "locator", "sign_In_Button",
                "reason", "ungrounded"));
        HuntRunLog.cycleDone(1, 0, 0);
        HuntRunLog.finished("CYCLE_CAP", 1, 0, 0);
        Assert.assertTrue(true);
    }
}
