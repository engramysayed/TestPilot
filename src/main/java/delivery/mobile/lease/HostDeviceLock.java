package delivery.mobile.lease;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Host-scoped physical device lock keyed by {@code (hostId, serialFingerprint)}.
 * Lock files live under a host lock root — <strong>not</strong> under a tenant directory —
 * so two tenants cannot obtain separate locks for the same phone.
 * <p>
 * Combines a per-key JVM {@link Semaphore} with an OS {@link FileLock} so concurrent
 * agent threads and processes contend on the same physical identity. The semaphore is
 * used (not {@code ReentrantLock}) so cleanup/ACK on another thread can release safely.
 */
public final class HostDeviceLock implements AutoCloseable {
    private static final ConcurrentHashMap<String, Semaphore> LOCAL = new ConcurrentHashMap<>();

    private final Path lockFile;
    private final String key;
    private final Semaphore local;
    private final FileChannel channel;
    private final FileLock osLock;
    private boolean closed;

    private HostDeviceLock(Path lockFile, String key, Semaphore local, FileChannel channel, FileLock osLock) {
        this.lockFile = lockFile;
        this.key = key;
        this.local = local;
        this.channel = channel;
        this.osLock = osLock;
    }

    /**
     * Non-blocking try-acquire. Empty when another thread/process holds the physical lock.
     */
    public static Optional<HostDeviceLock> tryAcquire(Path lockRoot, String hostId, String serialFingerprint) {
        Objects.requireNonNull(lockRoot, "lockRoot");
        if (hostId == null || hostId.isBlank() || serialFingerprint == null || serialFingerprint.isBlank()) {
            throw new IllegalArgumentException("hostId and serialFingerprint are required");
        }
        Path lockFile = lockPath(lockRoot, hostId, serialFingerprint);
        String key = lockFile.toAbsolutePath().normalize().toString();
        Semaphore local = LOCAL.computeIfAbsent(key, ignored -> new Semaphore(1));
        if (!local.tryAcquire()) {
            return Optional.empty();
        }
        FileChannel channel = null;
        FileLock osLock = null;
        try {
            Files.createDirectories(lockFile.getParent());
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                osLock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                osLock = null;
            }
            if (osLock == null) {
                channel.close();
                local.release();
                return Optional.empty();
            }
            channel.truncate(0);
            channel.write(StandardCharsets.UTF_8.encode(hostId + "\n" + serialFingerprint + "\n"));
            channel.force(true);
            return Optional.of(new HostDeviceLock(lockFile, key, local, channel, osLock));
        } catch (IOException e) {
            try {
                if (osLock != null) {
                    osLock.release();
                }
            } catch (IOException ignored) {
                // best-effort
            }
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException ignored) {
                // best-effort
            }
            local.release();
            throw new DeviceLeaseException(DeviceLeaseErrorCode.HOST_LOCK_BUSY,
                    "host lock I/O failed: " + e.getMessage());
        }
    }

    /** Lock file path uses only host + fingerprint — never tenantId. */
    public static Path lockPath(Path lockRoot, String hostId, String serialFingerprint) {
        String hostSeg = safeSegment(hostId);
        String fpSeg = safeSegment(serialFingerprint);
        return lockRoot.resolve("hosts").resolve(hostSeg).resolve("devices").resolve(fpSeg + ".lock");
    }

    static String safeSegment(String raw) {
        String trimmed = raw.trim();
        if (trimmed.matches("[A-Za-z0-9._-]{1,64}")) {
            return trimmed;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 32);
        } catch (Exception e) {
            return Integer.toHexString(trimmed.hashCode());
        }
    }

    public Path lockFile() {
        return lockFile;
    }

    public String physicalKey() {
        return key;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            osLock.release();
        } catch (IOException ignored) {
            // best-effort
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // best-effort
        } finally {
            local.release();
        }
    }
}
