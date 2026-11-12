package delivery.agent.harness;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Exclusive lock for {@code cursor-sdk-state/} mutations. Released on close or process exit.
 */
public final class AgentStoreLock implements AutoCloseable {
    public static final String LOCK_FILE = "cursor-sdk-state.store.lock";

    private final FileChannel channel;
    private final FileLock lock;

    private AgentStoreLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static AgentStoreLock acquire(Path taskDir, Duration timeout) throws IOException {
        if (taskDir == null) {
            throw new IllegalArgumentException("taskDir is required");
        }
        Files.createDirectories(taskDir);
        Path lockFile = taskDir.resolve(LOCK_FILE);
        long deadline = System.nanoTime() + (timeout == null ? Duration.ofSeconds(30) : timeout).toNanos();
        IOException last = null;
        while (System.nanoTime() < deadline) {
            try {
                FileChannel ch = FileChannel.open(lockFile,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock fl = ch.tryLock();
                if (fl != null) {
                    return new AgentStoreLock(ch, fl);
                }
                ch.close();
            } catch (OverlappingFileLockException e) {
                last = new IOException("overlapping store lock", e);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted waiting for store lock", ie);
            }
        }
        throw new AgentHarnessException("store lock timeout"
                + (last == null ? "" : ": " + last.getMessage()));
    }

    @Override
    public void close() throws IOException {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } finally {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        }
    }
}
