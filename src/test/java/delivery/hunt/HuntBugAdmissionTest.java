package delivery.hunt;

import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class HuntBugAdmissionTest {
    private static Map<String, Object> bug(String title, String sev, String actual) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("severity", sev);
        m.put("actual", actual);
        m.put("expected", actual);
        return m;
    }

    @Test public void plannerOracleSourceIgnored() {
        Map<String, Object> b = bug("maybe broken", "major", "x");
        b.put("oracleSource", true);
        assertFalse(HuntBugAdmission.decide(b, List.of(), List.of(), "<html/>").admit(),
                "planner must not self-certify oracleSource");
    }

    @Test public void softAssertTextDroppedWithoutCopy() {
        Map<String, Object> b = bug("Assert failed: assert_text (Welcome toast)", "major", "not found");
        var d = HuntBugAdmission.decide(b, List.of(), List.of(), "<html>no toast here</html>");
        assertFalse(d.admit(), d.reason());
    }

    @Test public void softAssertTextKeptWhenWarnAndCopyOnPage() {
        Map<String, Object> b = bug("Assert failed: assert_text (Saved)", "warn", "Saved");
        var d = HuntBugAdmission.decide(b, List.of(), List.of(), "<div>Saved successfully</div>");
        assertTrue(d.admit(), d.reason());
        assertEquals(d.evidence(), HuntBugAdmission.Evidence.COPY_MATCHED_WARN);
    }

    @Test public void minorSeverityNotEnoughForSoftToast() {
        Map<String, Object> b = bug("Assert failed: assert_text (Saved)", "minor", "Saved");
        assertFalse(HuntBugAdmission.decide(b, List.of(), List.of(), "<div>Saved</div>").admit());
    }

    @Test public void speculativePlannerBugRejected() {
        Map<String, Object> b = bug("Maybe broken checkout", "major", "looks wrong");
        assertFalse(HuntBugAdmission.decide(b, List.of(), List.of(), "<html/>").admit());
    }

    @Test public void mapBoundRequiresLocatorOverlap() {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("type", "assert_visible");
        fail.put("locator", "#submit");
        fail.put("status", "fail");
        Map<String, Object> b = bug("Assert failed somewhere else", "major", "nope");
        assertFalse(HuntBugAdmission.decide(b, List.of(), List.of(fail), "<html/>").admit());
        Map<String, Object> related = bug("Assert failed: assert_visible #submit", "major", "missing");
        assertTrue(HuntBugAdmission.decide(related, List.of(), List.of(fail), "<html/>").admit());
    }
}
