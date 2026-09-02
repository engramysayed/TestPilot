package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

public class JobProgressTrackerTest {

    @Test
    public void effectiveTotalPrefersReservedJobTotal() {
        JobProgressTracker progress = new JobProgressTracker();
        progress.update(0, 7, "Execute starting"); // 5 cases + design + save
        Assert.assertEquals(progress.effectiveTotal(5), 7);
        progress.update(5, progress.effectiveTotal(5), "Design compare");
        Assert.assertEquals(progress.current(), 5);
        Assert.assertEquals(progress.total(), 7);
        Assert.assertNotEquals(progress.current(), progress.total());
    }

    @Test
    public void effectiveTotalFallsBackToPhaseSizeWhenUnset() {
        JobProgressTracker progress = new JobProgressTracker();
        Assert.assertEquals(progress.effectiveTotal(3), 3);
    }
}
