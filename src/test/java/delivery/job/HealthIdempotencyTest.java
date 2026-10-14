package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class HealthIdempotencyTest {
    @Test public void rejectionThenRetryCreatesARealJob() throws Exception {
        var store = new IdempotencyStore(Files.createTempDirectory("health-idem").resolve("keys.json"));
        Set<String> jobs = new HashSet<>();
        Assert.expectThrows(JobAdmissionException.class, () -> store.submit("key", "body", jobs::contains,
                id -> { throw new JobAdmissionException("QUEUE_SATURATED", "full"); }));
        var admitted = store.submit("key", "body", jobs::contains, jobs::add);
        Assert.assertFalse(admitted.replay());
        Assert.assertTrue(jobs.contains(admitted.jobId()));
        var replay = store.submit("key", "body", jobs::contains, id -> Assert.fail("Replay prepared new inputs"));
        Assert.assertTrue(replay.replay());
        Assert.assertEquals(replay.jobId(), admitted.jobId());
        Assert.expectThrows(IdempotencyStore.Conflict.class,
                () -> store.submit("key", "changed", jobs::contains, jobs::add));
    }

    @Test public void pendingReservationsRecoverEitherSideOfJobCommit() throws Exception {
        Path file = Files.createTempDirectory("health-idem-recovery").resolve("keys.json");
        Files.writeString(file, "{\"key\":{\"jobId\":\"reserved\",\"bodyHash\":\"body\",\"state\":\"PENDING\"}}");
        var store = new IdempotencyStore(file);
        var recovered = store.submit("key", "body", id -> true, id -> Assert.fail("Already committed"));
        Assert.assertTrue(recovered.replay());
        Files.writeString(file, "{\"key\":{\"jobId\":\"reserved\",\"bodyHash\":\"body\",\"state\":\"PENDING\"}}");
        Set<String> jobs = new HashSet<>();
        var resumed = store.submit("key", "body", jobs::contains, jobs::add);
        Assert.assertFalse(resumed.replay());
        Assert.assertEquals(resumed.jobId(), "reserved");
    }

    @Test public void concurrentSubmissionsAdmitOnce() throws Exception {
        Path file = Files.createTempDirectory("health-idem-race").resolve("keys.json");
        Set<String> jobs = ConcurrentHashMap.newKeySet();
        AtomicInteger calls = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<IdempotencyStore.Submission> submit = () -> new IdempotencyStore(file).submit("key", "body",
                    jobs::contains, id -> { calls.incrementAndGet(); jobs.add(id); });
            var a = pool.submit(submit); var b = pool.submit(submit);
            Assert.assertEquals(a.get().jobId(), b.get().jobId());
            Assert.assertEquals(calls.get(), 1);
        }
    }
}
