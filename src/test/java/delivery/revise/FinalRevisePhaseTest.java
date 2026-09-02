package delivery.revise;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class FinalRevisePhaseTest {

    @Test
    public void parseVerdict_demotesAndBlocks() {
        String raw = """
                {
                  "jobVerdict": "BLOCK",
                  "cases": [
                    {
                      "tcId": "TC_01",
                      "verdict": "DEMOTE",
                      "newStatus": "PARTIAL",
                      "missingIntents": ["Confirm Logout"],
                      "wrongness": ["Bound title instead of Logout"],
                      "notes": "bad pass"
                    }
                  ],
                  "notes": "suite issue"
                }
                """;
        FinalRevisePhase.ParsedVerdict parsed = FinalRevisePhase.parseVerdict(raw);
        Assert.assertEquals(parsed.jobVerdict(), FinalReviseResult.JobVerdict.BLOCK);
        Assert.assertEquals(parsed.demoteIds(), List.of("TC_01"));
        Assert.assertEquals(parsed.caseVerdicts().get("TC_01").newStatus(), TcDraftStatus.PARTIAL);
    }

    @Test
    public void applyDemotes_onlyChangesPassed() {
        TcDraft passed = draft("TC_01", TcDraftStatus.PASSED);
        TcDraft todo = draft("TC_02", TcDraftStatus.TODO);
        Map<String, FinalRevisePhase.CaseVerdict> verdicts = Map.of(
                "TC_01", new FinalRevisePhase.CaseVerdict(
                        "TC_01", "DEMOTE", TcDraftStatus.PARTIAL,
                        List.of("missing"), List.of("wrong"), "n"),
                "TC_02", new FinalRevisePhase.CaseVerdict(
                        "TC_02", "DEMOTE", TcDraftStatus.TODO,
                        List.of(), List.of(), "")
        );
        List<TcDraft> out = FinalRevisePhase.applyDemotes(List.of(passed, todo), verdicts);
        Assert.assertEquals(out.get(0).status(), TcDraftStatus.PARTIAL);
        Assert.assertTrue(out.get(0).failureReason().startsWith("FINAL_REVISE:"));
        Assert.assertEquals(out.get(1).status(), TcDraftStatus.TODO);
    }

    @Test
    public void softBlocked_setsPortalStatus() {
        FinalReviseResult blocked = new FinalReviseResult(
                List.of(), FinalReviseResult.JobVerdict.BLOCK, "md", "FINAL_REVISE_BLOCK", true);
        Assert.assertTrue(blocked.softBlocked());
        Assert.assertEquals(blocked.portalJobStatus(), "COMPLETED_WITH_BLOCK");

        FinalReviseResult ship = new FinalReviseResult(
                List.of(), FinalReviseResult.JobVerdict.SHIP, "md", "ok", true);
        Assert.assertFalse(ship.softBlocked());
        Assert.assertEquals(ship.portalJobStatus(), "COMPLETED");
    }

    @Test
    public void extractText_fromAnthropicContent() throws Exception {
        String body = """
                {"content":[{"type":"text","text":"{\\"jobVerdict\\":\\"SHIP\\",\\"cases\\":[]}"}]}
                """;
        String text = AgentRouterClient.extractText(body);
        Assert.assertTrue(text.contains("SHIP"));
    }

    private static TcDraft draft(String id, TcDraftStatus status) {
        return new TcDraft(
                id, "t", "1. Click Login", "ok", status,
                List.of(new ProvenStep(id, "Page", "elementAction", "click",
                        "id", "login", "", "", "", true, "intent:CLICK")),
                List.of(), false, 0, "", "", "", 0, "", "ollama", "", "");
    }
}
