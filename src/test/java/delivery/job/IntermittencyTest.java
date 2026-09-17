package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class IntermittencyTest {

    @Test
    public void oneRetryIsNotFlaky() {
        Assert.assertEquals(Intermittency.verdict(List.of(
                new Intermittency.Outcome("TC_01", false),
                new Intermittency.Outcome("TC_01", true)
        )), Intermittency.Verdict.INSUFFICIENT_SAMPLE);
        Assert.assertEquals(Intermittency.verdict(List.of(
                new Intermittency.Outcome("TC_01", false),
                new Intermittency.Outcome("TC_01", true),
                new Intermittency.Outcome("TC_01", false)
        )), Intermittency.Verdict.INTERMITTENT);
    }
}
