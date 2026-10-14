package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
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

    @Test
    public void typeValuesAreRedactedInDetail() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "type");
        row.put("locator", "#password");
        row.put("value", "SuperSecretOTP-999");
        row.put("status", "ok");
        String detail = HuntRunLog.detailOf(row);
        Assert.assertFalse(detail.contains("SuperSecretOTP-999"), detail);
        Assert.assertTrue(detail.contains("value=(redacted)"), detail);
        Assert.assertTrue(detail.contains("locator="), detail);
        // action() must also not throw / leak via LogsManager path
        HuntRunLog.action(row);
    }
}
