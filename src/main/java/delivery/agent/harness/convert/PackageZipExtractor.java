package delivery.agent.harness.convert;

import delivery.job.ArtifactIntegrityException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Safe extraction of emit {@code package.zip} into a confined workspace directory.
 * Rules mirror {@code delivery.mobile.transport.MobileInputZipValidator}.
 */
public final class PackageZipExtractor {
    public static final int DEFAULT_MAX_ENTRIES = 10_000;
    public static final long DEFAULT_MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;

    private PackageZipExtractor() {
    }

    public static void extract(Path zipFile, Path destRoot) throws IOException {
        extract(zipFile, destRoot, DEFAULT_MAX_ENTRIES, DEFAULT_MAX_UNCOMPRESSED_BYTES);
    }

    public static void extract(Path zipFile, Path destRoot, int maxEntries, long maxUncompressedBytes)
            throws IOException {
        if (zipFile == null || !Files.isRegularFile(zipFile)) {
            throw new ArtifactIntegrityException("zip file missing");
        }
        if (destRoot == null) {
            throw new IllegalArgumentException("destRoot is required");
        }
        Path destReal = destRoot.toAbsolutePath().normalize();
        Files.createDirectories(destReal.getParent());
        if (Files.exists(destReal)) {
            deleteRecursive(destReal);
        }
        Files.createDirectories(destReal);

        int entries = 0;
        long uncompressed = 0L;
        try (InputStream in = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries++;
                if (entries > maxEntries) {
                    deleteRecursive(destReal);
                    throw new ArtifactIntegrityException("zip entry count exceeds limit");
                }
                rejectUnsafeName(entry.getName());
                Path target = destReal.resolve(entry.getName()).normalize();
                if (!target.startsWith(destReal)) {
                    deleteRecursive(destReal);
                    throw new ArtifactIntegrityException("zip entry escapes workspace: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    long written = copyBounded(zis, target, maxUncompressedBytes - uncompressed);
                    uncompressed += written;
                    if (uncompressed > maxUncompressedBytes) {
                        deleteRecursive(destReal);
                        throw new ArtifactIntegrityException("uncompressed zip payload exceeds limit");
                    }
                }
                zis.closeEntry();
            }
            if (entries == 0) {
                deleteRecursive(destReal);
                throw new ArtifactIntegrityException("empty zip");
            }
        } catch (ArtifactIntegrityException e) {
            throw e;
        } catch (IOException e) {
            deleteRecursive(destReal);
            throw new ArtifactIntegrityException("corrupt zip: " + e.getMessage());
        }
    }

    static void rejectUnsafeName(String name) {
        if (name == null || name.isEmpty()) {
            throw new ArtifactIntegrityException("empty zip entry name");
        }
        if (name.indexOf('\0') >= 0) {
            throw new ArtifactIntegrityException("null byte in entry name");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//")) {
            throw new ArtifactIntegrityException("absolute zip path refused");
        }
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) {
                throw new ArtifactIntegrityException("path traversal refused: " + name);
            }
        }
    }

    private static long copyBounded(InputStream in, Path target, long maxBytes) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        try (java.io.OutputStream out = Files.newOutputStream(
                target,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS)) {
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total > maxBytes) {
                        throw new ArtifactIntegrityException("entry exceeds remaining budget");
                    }
                }
            }
        }
        return total;
    }

    public static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
