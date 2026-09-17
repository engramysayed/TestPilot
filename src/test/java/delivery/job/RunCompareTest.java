package delivery.job;

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
}
