package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessSupervisorTest {

    @Test
    public void hangingProcessIsKilledWithinDeadlineAndReleasesTheSlot() throws Exception {
        ProcessBuilder pb = hangingBuilder();
        ProcessSupervisor.Result result = ProcessSupervisor.run(
                pb, Duration.ofMillis(900), () -> false, 4000);
        Assert.assertTrue(result.timedOut(), result.output());
        Assert.assertFalse(result.alive(), "worker slot must be released");
        Assert.assertTrue(result.output().length() <= 4000);
    }

    @Test
    public void cancelStopsTheProcessBeforeDeadlineAndDoesNotPublish() throws Exception {
        AtomicBoolean cancel = new AtomicBoolean(true);
        ProcessSupervisor.Result result = ProcessSupervisor.run(
                hangingBuilder(), Duration.ofSeconds(20), cancel::get, 2000);
        Assert.assertTrue(result.cancelled());
        Assert.assertFalse(result.timedOut());
        Assert.assertFalse(result.alive());
        Assert.assertFalse(result.completedNormally());
    }

    private static ProcessBuilder hangingBuilder() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows
                ? new ProcessBuilder("ping", "-n", "40", "127.0.0.1")
                : new ProcessBuilder("sleep", "40");
    }
}
