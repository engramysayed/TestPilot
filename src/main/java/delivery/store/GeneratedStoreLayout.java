package delivery.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Generated TC library lives under the domain project root:
 * {@code <storeRoot>/<domain>/<projectId>/generated/}.
 * Legacy flat {@code <storeRoot>/<projectId>/generated/} is moved once when the
 * ops/domain folder already exists.
 */
public final class GeneratedStoreLayout {
    private GeneratedStoreLayout() {
    }

    public static Path resolveGeneratedDir(Path storeRoot, Path projectRoot, String projectId) {
        if (storeRoot == null) {
            throw new IllegalArgumentException("storeRoot is required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        Path flat = storeRoot.resolve(projectId).resolve("generated");
        Path target = projectRoot == null ? flat : projectRoot.resolve("generated");
        migrateIfNeeded(flat, target);
        return target;
    }

    static void migrateIfNeeded(Path flat, Path target) {
        if (flat == null || target == null || flat.equals(target)) {
            return;
        }
        Path flatExcel = flat.resolve("latest.xlsx");
        Path targetExcel = target.resolve("latest.xlsx");
        if (!Files.isRegularFile(flatExcel) || Files.isRegularFile(targetExcel)) {
            return;
        }
        try {
            Files.createDirectories(target);
            try (Stream<Path> files = Files.list(flat)) {
                for (Path file : files.toList()) {
                    Files.move(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            deleteIfEmpty(flat);
            deleteIfEmpty(flat.getParent());
        } catch (IOException ignored) {
            // Resolve still returns the nested path; next write creates it.
        }
    }

    private static void deleteIfEmpty(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> children = Files.list(dir)) {
            if (children.findAny().isPresent()) {
                return;
            }
        }
        Files.deleteIfExists(dir);
    }

}
