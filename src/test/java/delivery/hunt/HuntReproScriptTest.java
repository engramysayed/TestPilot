package delivery.hunt;

import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class HuntReproScriptTest {
    @Test public void capsAtFiveAndRedactsTypeValues() {
        List<Map<String, Object>> log = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", i == 1 ? "type" : "click");
            m.put("locator", "#b" + i);
            m.put("value", "secret-" + i);
            m.put("status", "ok");
            log.add(m);
        }
        var steps = HuntReproScript.fromActionLog(log);
        assertEquals(steps.size(), 5);
        assertTrue(steps.get(1).contains("(redacted)"));
        assertFalse(HuntReproScript.format(steps).contains("secret-"));
    }
}
