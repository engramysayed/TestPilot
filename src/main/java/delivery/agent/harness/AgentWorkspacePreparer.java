package delivery.agent.harness;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Materializes {@code taskDir/workspace/} as a copy of the source tree (no symlink escape).
 */
public final class AgentWorkspacePreparer {

    public Path ensureIsolatedCopy(Path taskDir, Path sourceWorkspace) throws IOException {
        if (taskDir == null || sourceWorkspace == null) {
            throw new IllegalArgumentException("taskDir and sourceWorkspace are required");
        }
        if (!Files.isDirectory(sourceWorkspace)) {
            throw new AgentHarnessException("source workspace is not a directory: " + sourceWorkspace);
        }
        Path sourceReal = sourceWorkspace.toAbsolutePath().normalize().toRealPath();
        if (Files.isSymbolicLink(sourceWorkspace)) {
            throw new AgentHarnessException("source workspace cannot be a symlink");
        }
        Path target = AgentWorkspacePaths.isolatedWorkspace(taskDir);
        if (Files.isDirectory(target) && directoryNonEmpty(target)) {
            return target.toAbsolutePath().normalize();
        }
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            deleteRecursive(target);
        }
        copyTreeNoSymlinks(sourceReal, target);
        return target.toAbsolutePath().normalize();
    }

    private static boolean directoryNonEmpty(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isPresent();
        }
    }

    private static void copyTreeNoSymlinks(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) {
                    throw new AgentHarnessException("symlink in source workspace: " + dir);
                }
                Path rel = source.relativize(dir);
                Path dest = target.resolve(rel);
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new AgentHarnessException("symlink file in source: " + file);
                }
                Path rel = source.relativize(file);
                Files.copy(file, target.resolve(rel), LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path root) throws IOException {
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
