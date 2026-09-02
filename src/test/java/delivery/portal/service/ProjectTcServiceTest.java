package delivery.portal.service;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class ProjectTcServiceTest {
    @Test
    public void timelineMarksPassedFailedRemaining() {
        ProvenStep ok = new ProvenStep(
                "TC1", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        TcDraft draft = new TcDraft(
                "TC1", "Checkout",
                "1. Click checkout\n2. Click finish\n3. Confirm thank you",
                "Thank you shown",
                TcDraftStatus.PARTIAL, List.of(ok), List.of(), false,
                1, "Click finish", "No candidate", "", 0, "https://x/cart");
        List<Map<String, Object>> timeline = ProjectTcService.buildTimeline(draft);
        Assert.assertTrue(timeline.stream().anyMatch(r -> "passed".equals(r.get("state"))));
        Assert.assertTrue(timeline.stream().anyMatch(r -> "failed".equals(r.get("state"))));
        Assert.assertTrue(timeline.stream().anyMatch(r -> "remaining".equals(r.get("state"))));
    }
}
