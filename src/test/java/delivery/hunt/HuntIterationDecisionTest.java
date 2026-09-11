package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HuntIterationDecisionTest {

    @Test
    public void parsesFinishIterationDecision() {
        HuntPlannerDecision d = HuntPlannerDecision.parse("""
                {"decision":"finish_iteration","rationale":"plan done","actions":[],"bugs":[],"scenarios":[]}
                """);
        Assert.assertEquals(d.decision(), HuntPlannerDecision.Decision.FINISH_ITERATION);
    }

    @Test
    public void stopIterationFlagMapsToFinishIteration() {
        HuntPlannerDecision d = HuntPlannerDecision.parse("""
                {"decision":"continue","stopIteration":true,"rationale":"x","actions":[],"bugs":[],"scenarios":[]}
                """);
        Assert.assertEquals(d.decision(), HuntPlannerDecision.Decision.FINISH_ITERATION);
    }

    @Test
    public void finishStillStopsHunt() {
        HuntPlannerDecision d = HuntPlannerDecision.parse("""
                {"decision":"finish","rationale":"done","actions":[],"bugs":[],"scenarios":[]}
                """);
        Assert.assertEquals(d.decision(), HuntPlannerDecision.Decision.FINISH);
    }

    @Test
    public void promptOmitsOtpWhenNotConfigured() {
        HuntPlanner.Context ctx = new HuntPlanner.Context(
                "brief", 1, 8, 5, 0, 5, "", null, java.util.List.of(),
                "", "ollama", "map", false, "", "mode=explore", "auto", "",
                true, "alice", false, false, 1, 2, "", "");
        String prompt = OllamaHuntPlanner.buildUserPrompt(ctx);
        Assert.assertFalse(prompt.contains("TARGET_OTP"), prompt);
        Assert.assertFalse(prompt.contains("OTP token"), prompt);
    }
}
