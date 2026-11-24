package delivery.agent.validation.generate;

import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GenerateRepairWriteBackTest {

    @Test
    public void repairedPayloadReplacesTheCases() {
        String json = "{\"testCases\":[{\"tcId\":\"TC_01\",\"title\":\"Pay\",\"steps\":\"Tap Pay\","
                + "\"expectedResult\":\"Paid\"}]}";
        var cases = GenerateHarnessBridge.casesFromAgentText(json, Map.of());
        assertEquals(cases.size(), 1);
        assertEquals(cases.get(0).tcId(), "TC_01");
        assertTrue(cases.get(0).steps().contains("Tap Pay"));
    }

    @Test
    public void localRepairAsksForTheFailingChecks() {
        String user = GenerateHarnessBridge.localRepairUser(
                "tcId 'TC_01': title is blank. Fix: Add a short title.",
                "{\"testCases\":[]}");
        assertTrue(user.contains("title is blank"), user);
        assertTrue(user.contains("testCases"), user);
    }

    @Test
    public void emptyAgentTextLeavesNothingToApply() {
        assertTrue(GenerateHarnessBridge.casesFromAgentText("no json here", Map.of()).isEmpty());
    }
}
