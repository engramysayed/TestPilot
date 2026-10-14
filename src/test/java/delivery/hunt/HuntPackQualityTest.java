package delivery.hunt;

import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class HuntPackQualityTest {
    @Test public void countsFirstPassFlakyConfirmed() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("title", "Net fail");
        a.put("oracleSource", true);
        a.put("dupCount", 1);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("title", "Same again");
        b.put("dupCount", 3);
        var c = HuntPackQuality.of(List.of(a, b));
        assertEquals(c.confirmed(), 2);
        assertEquals(c.firstPass(), 1);
        assertEquals(c.flaky(), 1);
        assertEquals(c.oracle(), 1);
        assertTrue(HuntPackQuality.summaryLines(c).contains("First-pass rate"));
    }

    @Test public void oracleAloneIsFirstPassNotConfirmed() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("oracleSource", true);
        a.put("dupCount", 1);
        assertEquals(HuntPackQuality.qaStatusFor(a), "FIRST_PASS");
        a.put("dupCount", 2);
        assertEquals(HuntPackQuality.qaStatusFor(a), "CONFIRMED");
    }
}
