package delivery.agent.harness;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AgentStoreLockTest {

    @Test
    public void secondAcquireTimesOut() throws Exception {
        Path taskDir = Files.createTempDirectory("store-lock-");
        try (AgentStoreLock first = AgentStoreLock.acquire(taskDir, Duration.ofSeconds(2))) {
            CountDownLatch latched = new CountDownLatch(1);
            AtomicBoolean timedOut = new AtomicBoolean(false);
            Thread other = new Thread(() -> {
                try {
                    AgentStoreLock.acquire(taskDir, Duration.ofMillis(300));
                } catch (AgentHarnessException e) {
                    if (e.getMessage() != null && e.getMessage().contains("store lock timeout")) {
                        timedOut.set(true);
                    }
                } catch (Exception e) {
                    /* unexpected */
                } finally {
                    latched.countDown();
                }
            });
            other.start();
            Assert.assertTrue(latched.await(5, TimeUnit.SECONDS));
            Assert.assertTrue(timedOut.get(), "second acquirer should time out");
        }
    }
}
