package delivery.identity;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cross-process publication lock (OS file lock) with in-JVM serialization so
 * threads in the same process do not hit {@link OverlappingFileLockException}.
 *
 * <p>LOCAL is bounded (access-order LRU, {@value MAX_LOCKS} entries). Evicting a lock
 * while another thread holds it is safe: the OS file lock still serializes across
 * JVMs, and the {@code tryLock} loop below absorbs any transient
 * {@link OverlappingFileLockException} until the previous holder releases.
 */
public final class PublicationLock {
    static final int MAX_LOCKS = 10_000;

    private static final Map<String, ReentrantLock> LOCAL = Collections.synchronizedMap(
            new LinkedHashMap<String, ReentrantLock>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ReentrantLock> eldest) {
                    return size() > MAX_LOCKS;
                }
            });

    @FunctionalInterface
    public interface Body<T> {
        T run() throws Exception;
    }

    private PublicationLock() {
    }

    public static void run(Path lockFile, Body<Void> body) throws Exception {
        call(lockFile, () -> {
            body.run();
            return null;
        });
    }

    public static <T> T call(Path lockFile, Body<T> body) throws Exception {
        if (lockFile == null) {
            throw new IllegalArgumentException("lock file is required");
        }
        Files.createDirectories(lockFile.getParent());
        String key = lockFile.toAbsolutePath().normalize().toString();
        ReentrantLock local = LOCAL.computeIfAbsent(key, ignored -> new ReentrantLock());
        local.lock();
        try (FileChannel channel = FileChannel.open(
                lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock osLock = null;
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (osLock == null) {
                try {
                    osLock = channel.tryLock();
                } catch (OverlappingFileLockException ignored) {
                    osLock = null;
                }
                if (osLock == null) {
                    if (System.nanoTime() > deadline) {
                        throw new IllegalStateException("publication lock timed out: " + lockFile);
                    }
                    Thread.sleep(15);
                }
            }
            try {
                return body.run();
            } finally {
                osLock.release();
            }
        } finally {
            local.unlock();
        }
    }
}
