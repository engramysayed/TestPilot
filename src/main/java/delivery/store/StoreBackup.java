package delivery.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Filesystem snapshot/restore for the portal store. Does not claim cross-host
 * deployment validation; that remains a release drill.
 */
public final class StoreBackup {
    private StoreBackup() {
    }

    public static Path snapshot(Path storeRoot, Path destDir) throws IOException {
        if (storeRoot == null || !Files.isDirectory(storeRoot)) {
            throw new IllegalArgumentException("store root is required");
        }
        if (destDir == null) {
            throw new IllegalArgumentException("backup destination is required");
        }
        Files.createDirectories(destDir);
        Path dest = destDir.resolve("snapshot-" + Instant.now().toEpochMilli());
        Files.createDirectories(dest);
        copyTree(storeRoot.toAbsolutePath().normalize(), dest);
        Files.writeString(dest.resolve("BACKUP_MANIFEST.txt"),
                "createdAt=" + Instant.now() + "\nfiles=" + countFiles(dest) + "\n");
        return dest;
    }

    public static void restore(Path snapshot, Path storeRoot) throws IOException {
        if (snapshot == null || !Files.isDirectory(snapshot)) {
            throw new IllegalArgumentException("backup snapshot is required");
        }
        if (storeRoot == null) {
            throw new IllegalArgumentException("store root is required");
        }
        Path root = storeRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path snap = snapshot.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(snap)) {
            for (Path source : stream.toList()) {
                if (source.equals(snap)) {
                    continue;
                }
                Path rel = snap.relativize(source);
                if ("BACKUP_MANIFEST.txt".equals(rel.toString())) {
                    continue;
                }
                Path target = root.resolve(rel).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("refusing to restore outside store root: " + rel);
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file)));
        } catch (Exception e) {
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("checksum failed", e);
        }
    }

    private static void copyTree(Path source, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path p : stream.toList()) {
                Path rel = source.relativize(p);
                Path target = dest.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static int countFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }
        return files.size();
    }

    public static boolean looksLikeStoreFile(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".zip") || n.endsWith(".xlsx") || n.endsWith(".sha256") || n.endsWith(".json");
    }
}
