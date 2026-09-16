package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PublicationLockTest {

    @Test
    public void threadsInOneJvmSerializeOnTheSameLockFile() throws Exception {
        Path lock = Files.createTempDirectory("pub-lock").resolve("project.publish.lock");
        AtomicInteger counter = new AtomicInteger();
        CyclicBarrier start = new CyclicBarrier(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int i = 0; i < 8; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    PublicationLock.call(lock, () -> {
                        int seen = counter.get();
                        Thread.sleep(5);
                        counter.set(seen + 1);
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        Assert.assertTrue(done.await(20, TimeUnit.SECONDS));
        Assert.assertEquals(counter.get(), 8);
    }

    @Test
    public void secondProcessWaitsForOsFileLock() throws Exception {
        Path dir = Files.createTempDirectory("pub-proc");
        Path lock = dir.resolve("cross.publish.lock");
        Path flag = dir.resolve("held.flag");
        Path log = dir.resolve("child.log");
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.isBlank()) {
            cp = System.getProperty("java.class.path");
        }
        Process child = new ProcessBuilder(
                javaBin(),
                "-cp", cp,
                PublicationLockHold.class.getName(),
                lock.toAbsolutePath().toString(),
                flag.toAbsolutePath().toString(),
                "1800")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        long waitFlag = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.isRegularFile(flag) && child.isAlive() && System.nanoTime() < waitFlag) {
            Thread.sleep(25);
        }
        Assert.assertTrue(Files.isRegularFile(flag),
                "child never took the lock: " + (Files.isRegularFile(log) ? Files.readString(log) : "no log"));

        long started = System.nanoTime();
        PublicationLock.call(lock, () -> "ok");
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Assert.assertTrue(waitedMs >= 400, "parent acquired lock too quickly (" + waitedMs + "ms); OS lock unused?");
        Assert.assertEquals(child.waitFor(), 0);
    }

    @Test
    public void osLockIsExercisedOnThisStoresFileSystemType() throws Exception {
        Path probe = Files.createDirectories(Path.of("target", "publication-lock-fs-probe"));
        java.nio.file.FileStore fileStore = Files.getFileStore(probe);
        String type = fileStore.type();
        Assert.assertNotNull(type);
        Assert.assertFalse(type.isBlank());
        Path lock = probe.resolve("fs.publish.lock");
        Path flag = probe.resolve("held.flag");
        Files.deleteIfExists(flag);
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.isBlank()) {
            cp = System.getProperty("java.class.path");
        }
        Process child = new ProcessBuilder(
                javaBin(),
                "-cp", cp,
                PublicationLockHold.class.getName(),
                lock.toAbsolutePath().toString(),
                flag.toAbsolutePath().toString(),
                "1200")
                .redirectErrorStream(true)
                .start();
        long waitFlag = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.isRegularFile(flag) && child.isAlive() && System.nanoTime() < waitFlag) {
            Thread.sleep(25);
        }
        Assert.assertTrue(Files.isRegularFile(flag),
                "OS lock helper failed on FileStore type=" + type + " name=" + fileStore.name());
        long started = System.nanoTime();
        PublicationLock.call(lock, () -> "ok");
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Assert.assertTrue(waitedMs >= 200,
                "OS file lock did not block on FileStore type=" + type + " (" + waitedMs + "ms)");
        Assert.assertEquals(child.waitFor(), 0);
    }

    private static String javaBin() {
        String home = System.getProperty("java.home");
        Path bin = Path.of(home, "bin",
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                        ? "java.exe" : "java");
        return bin.toString();
    }
}
