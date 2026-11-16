package delivery.agent.validation.automate;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/** Snapshots and isolated repair trees — original candidate preserved until promotion. */
public final class FrameworkAcceptanceWorkspace {
    private FrameworkAcceptanceWorkspace() {
    }

    public static Path acceptanceRoot(Path workDir) {
        return workDir.resolve("framework-acceptance");
    }

    public static Path snapshotOriginal(Path workDir, Path projectDir) throws IOException {
        Path dest = acceptanceRoot(workDir).resolve("original");
        replaceTree(projectDir, dest);
        return dest;
    }

    public static Path createRepairCandidate(Path workDir, Path projectDir) throws IOException {
        Path dest = acceptanceRoot(workDir).resolve("repair-candidate");
        replaceTree(projectDir, dest);
        return dest;
    }

    public static Path repairStagingDir(Path workDir, String taskId) {
        return acceptanceRoot(workDir).resolve("repair-staging").resolve(taskId);
    }

    public static Path repairCandidateBackup(Path workDir) {
        return acceptanceRoot(workDir).resolve("repair-candidate-backup");
    }

    public static Path reconciliationLockFile(Path workDir) {
        return acceptanceRoot(workDir).resolve("reconciliation.lock");
    }

    public static void promoteRepairCandidate(Path candidate, Path projectDir) throws IOException {
        if (candidate == null || !Files.isDirectory(candidate)) {
            throw new IllegalArgumentException("repair candidate missing");
        }
        deleteRecursive(projectDir);
        copyTree(candidate, projectDir);
    }

    private static void replaceTree(Path src, Path dest) throws IOException {
        deleteRecursive(dest);
        copyTree(src, dest);
    }

    static void copyTree(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(dir);
                Files.createDirectories(dest.resolve(rel));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(file);
                Files.copy(file, dest.resolve(rel), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteRecursive(Path root) throws IOException {
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