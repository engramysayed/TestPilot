package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RunCompareTest {

    @Test
    public void reportsFirstObservedMismatchOnPinnedCaseIdentity() {
        List<RunCompare.Step> first = List.of(
                new RunCompare.Step("TC_01", "open", "home", "home"),
                new RunCompare.Step("TC_01", "submit", "saved", "error"));
        List<RunCompare.Step> second = List.of(
                new RunCompare.Step("TC_01", "open", "home", "home"),
                new RunCompare.Step("TC_01", "submit", "saved", "saved"));
        RunCompare.Divergence d = RunCompare.firstMeaningful(first, second);
        Assert.assertEquals(d.index(), 1);
        Assert.assertEquals(d.field(), "observed");
        Assert.assertNull(RunCompare.firstMeaningful(second, second));
    }

    @Test
    public void recordedDraftsBecomeComparableSteps() {
        ProvenStep step = new ProvenStep(
                "TC1", "Home", "elementAction", "click",
                "id", "go", "", "text", "Welcome", false, "ok");
        TcDraft failed = new TcDraft(
                "TC1", "Home", "1. Click", "Welcome",
                TcDraftStatus.TODO, List.of(step), List.of(), false,
                -1, "", "assert failed", "", 0, "https://example/error");
        List<RunCompare.Step> steps = RunCompare.fromDrafts(List.of(failed));
        Assert.assertFalse(steps.isEmpty());
        Assert.assertEquals(steps.get(0).caseId(), "TC1");
        Assert.assertEquals(steps.get(0).expected(), "Welcome");
        Assert.assertTrue(steps.get(0).observed().contains("https://example/error")
                || steps.get(0).observed().contains("TODO"));
    }
}
