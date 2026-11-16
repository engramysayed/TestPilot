package delivery.agent.validation.automate;

import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.AgentWorkspacePaths;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transfers permitted generated-source changes from the Cursor-isolated task workspace into
 * {@code framework-acceptance/repair-candidate/}. FAKE provider never uses this path.
 */
public final class FrameworkRepairWorkspaceReconciler {
    private static final List<String> PERMITTED_PREFIXES = List.of(
            "src/main/java/project/pages/",
            "src/test/java/project/tests/generated/",
            "src/test/java/project/tests/todo/");

    private final AgentRunStore store;

    public FrameworkRepairWorkspaceReconciler() {
        this(new AgentRunStore());
    }

    FrameworkRepairWorkspaceReconciler(AgentRunStore store) {
        this.store = store == null ? new AgentRunStore() : store;
    }

    public record Manifest(
            List<String> copied,
            List<String> deleted,
            List<String> excluded,
            List<String> unchanged) {
    }

    public Manifest reconcile(
            Path workDir,
            Path projectRoot,
            String parentJobId,
            String taskId,
            Path repairCandidateRoot,
            Path expectedAgentWorkspace)
            throws FrameworkRepairReconciliationException, IOException {
        validateIds(parentJobId, taskId);
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            throw new FrameworkRepairReconciliationException("projectRoot is not a directory");
        }
        if (repairCandidateRoot == null || !Files.isDirectory(repairCandidateRoot)) {
            throw new FrameworkRepairReconciliationException("repair candidate is not a directory");
        }
        Path taskDir = store.taskDir(projectRoot, parentJobId, taskId);
        if (!Files.isDirectory(taskDir)) {
            throw new FrameworkRepairReconciliationException("task directory missing for " + taskId);
        }
        Path registeredWorkspace = AgentWorkspacePaths.isolatedWorkspace(taskDir);
        Path agentRoot = resolveRealDirectory(expectedAgentWorkspace, "agent workspace");
        Path registeredReal = resolveRealDirectory(registeredWorkspace, "registered workspace");
        if (!agentRoot.equals(registeredReal)) {
            throw new FrameworkRepairReconciliationException(
                    "agent workspace identity mismatch: expected " + registeredReal + " got " + agentRoot);
        }
        Path repairReal = resolveRealDirectory(repairCandidateRoot, "repair candidate");
        verifyRepairCandidateRoot(workDir, repairReal);
        try {
            Path taskDirReal = taskDir.toAbsolutePath().normalize().toRealPath();
            if (!agentRoot.startsWith(taskDirReal)) {
                throw new FrameworkRepairReconciliationException(
                        "agent workspace escapes task directory");
            }
        } catch (IOException e) {
            throw new FrameworkRepairReconciliationException("cannot verify task directory boundary", e);
        }

        AgentTask task = store.loadTask(projectRoot, parentJobId, taskId).orElseThrow(
                () -> new FrameworkRepairReconciliationException("task.json missing for " + taskId));
        if (task.taskType() != AgentTaskType.FRAMEWORK_ADAPTATION_V1) {
            throw new FrameworkRepairReconciliationException("unexpected task type: " + task.taskType());
        }
        if (task.providerId() != AgentProviderId.CURSOR) {
            throw new FrameworkRepairReconciliationException(
                    "reconciliation only applies to CURSOR tasks, not " + task.providerId());
        }
        if (task.taskStatus() != AgentTaskStatus.COMPLETED) {
            throw new FrameworkRepairReconciliationException(
                    "task not COMPLETED: " + task.taskStatus());
        }
        String taskWorkspace = task.workspacePath() == null ? "" : task.workspacePath().trim();
        if (taskWorkspace.isEmpty()) {
            throw new FrameworkRepairReconciliationException("task workspacePath unset");
        }
        Path taskWorkspacePath = resolveRealDirectory(Path.of(taskWorkspace), "task.workspacePath");
        if (!taskWorkspacePath.equals(agentRoot)) {
            throw new FrameworkRepairReconciliationException("task workspacePath does not match isolated workspace");
        }

        Path lockFile = FrameworkAcceptanceWorkspace.reconciliationLockFile(workDir);
        Files.createDirectories(lockFile.getParent());
        try (FileChannel lockChannel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            FileLock lock;
            try {
                lock = lockChannel.tryLock();
            } catch (java.nio.channels.OverlappingFileLockException e) {
                throw new FrameworkRepairReconciliationException(
                        "reconciliation already in progress for workDir", e);
            }
            if (lock == null) {
                throw new FrameworkRepairReconciliationException(
                        "reconciliation already in progress for workDir");
            }
            try {
            Path stagingRoot = FrameworkAcceptanceWorkspace.repairStagingDir(workDir, taskId);
            FrameworkAcceptanceWorkspace.deleteRecursive(stagingRoot);
            FrameworkAcceptanceWorkspace.copyTree(repairReal, stagingRoot);

            Manifest manifest = mergeAgentIntoStaging(agentRoot, stagingRoot, repairReal);

            Path backup = FrameworkAcceptanceWorkspace.repairCandidateBackup(workDir);
            FrameworkAcceptanceWorkspace.copyTree(repairReal, backup);
            try {
                applyStagingPermittedToRepair(stagingRoot, repairReal, manifest);
            } catch (IOException applyFailed) {
                FrameworkAcceptanceWorkspace.deleteRecursive(repairReal);
                FrameworkAcceptanceWorkspace.copyTree(backup, repairReal);
                throw applyFailed;
            } finally {
                FrameworkAcceptanceWorkspace.deleteRecursive(backup);
                FrameworkAcceptanceWorkspace.deleteRecursive(stagingRoot);
            }
                return manifest;
            } finally {
                lock.release();
            }
        }
    }

    private static Manifest mergeAgentIntoStaging(Path agentRoot, Path stagingRoot, Path repairReal)
            throws IOException {
        Set<String> agentPermitted = new HashSet<>();
        collectPermittedRelativePaths(agentRoot, agentPermitted);
        Set<String> stagingPermitted = new HashSet<>();
        collectPermittedRelativePaths(stagingRoot, stagingPermitted);
        Set<String> repairPermitted = new HashSet<>();
        collectPermittedRelativePaths(repairReal, repairPermitted);

        List<String> copied = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        for (String rel : agentPermitted) {
            Path src = agentRoot.resolve(rel);
            Path dest = stagingRoot.resolve(rel);
            if (!Files.isRegularFile(src, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isRegularFile(dest, LinkOption.NOFOLLOW_LINKS)
                    && fileContentEquals(src, dest)) {
                unchanged.add(rel);
                continue;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            copied.add(rel);
        }

        for (String rel : repairPermitted) {
            if (agentPermitted.contains(rel)) {
                continue;
            }
            Path dest = stagingRoot.resolve(rel);
            if (Files.isRegularFile(dest, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(dest);
                deleted.add(rel);
            }
        }

        scanExcluded(agentRoot, excluded);
        return new Manifest(List.copyOf(copied), List.copyOf(deleted), List.copyOf(excluded), List.copyOf(unchanged));
    }

    private static void applyStagingPermittedToRepair(
            Path stagingRoot, Path repairReal, Manifest manifest) throws IOException {
        for (String rel : manifest.copied()) {
            Path src = stagingRoot.resolve(rel);
            Path dest = repairReal.resolve(rel);
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
        }
        for (String rel : manifest.deleted()) {
            Path dest = repairReal.resolve(rel);
            Files.deleteIfExists(dest);
        }
        for (String rel : manifest.unchanged()) {
            Path src = stagingRoot.resolve(rel);
            Path dest = repairReal.resolve(rel);
            if (Files.isRegularFile(src, LinkOption.NOFOLLOW_LINKS)
                    && !fileContentEquals(src, dest)) {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            }
        }
    }

    private static void validateIds(String parentJobId, String taskId)
            throws FrameworkRepairReconciliationException {
        if (parentJobId == null || parentJobId.isBlank()) {
            throw new FrameworkRepairReconciliationException("parentJobId required");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new FrameworkRepairReconciliationException("taskId required");
        }
    }

    private static Path resolveRealDirectory(Path path, String label)
            throws FrameworkRepairReconciliationException {
        if (path == null) {
            throw new FrameworkRepairReconciliationException(label + " path is null");
        }
        try {
            if (Files.isSymbolicLink(path)) {
                throw new FrameworkRepairReconciliationException(label + " cannot be a symlink");
            }
            Path real = path.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(real)) {
                throw new FrameworkRepairReconciliationException(label + " is not a directory: " + path);
            }
            return real;
        } catch (IOException e) {
            throw new FrameworkRepairReconciliationException(label + " not accessible: " + path, e);
        }
    }

    private static void verifyRepairCandidateRoot(Path workDir, Path repairReal)
            throws FrameworkRepairReconciliationException {
        if (workDir == null || !Files.isDirectory(workDir)) {
            throw new FrameworkRepairReconciliationException("workDir is not a directory");
        }
        try {
            Path expectedPath = FrameworkAcceptanceWorkspace.acceptanceRoot(workDir).resolve("repair-candidate");
            Path expectedReal = expectedPath.toAbsolutePath().normalize().toRealPath();
            if (!repairReal.equals(expectedReal)) {
                throw new FrameworkRepairReconciliationException(
                        "repair candidate identity mismatch: expected " + expectedReal + " got " + repairReal);
            }
        } catch (IOException e) {
            throw new FrameworkRepairReconciliationException("cannot verify repair-candidate path", e);
        }
    }

    private static void collectPermittedRelativePaths(Path root, Set<String> out) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) {
                    throw new AgentHarnessException("symlink directory in workspace: " + dir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new AgentHarnessException("symlink file in workspace: " + file);
                }
                Path rel = root.relativize(file);
                String posix = toPosix(rel);
                if (isPermittedRelative(posix)) {
                    out.add(posix);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void scanExcluded(Path agentRoot, List<String> excluded) throws IOException {
        Files.walkFileTree(agentRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path rel = agentRoot.relativize(file);
                String posix = toPosix(rel);
                if (!isPermittedRelative(posix)) {
                    excluded.add(posix);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static boolean isPermittedRelative(String posixRelative) {
        if (posixRelative == null || posixRelative.isBlank()) {
            return false;
        }
        String norm = posixRelative.replace('\\', '/');
        if (norm.contains("..") || norm.startsWith("/")) {
            return false;
        }
        for (String prefix : PERMITTED_PREFIXES) {
            if (norm.equals(prefix.substring(0, prefix.length() - 1))) {
                return true;
            }
            if (norm.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String toPosix(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private static boolean fileContentEquals(Path a, Path b) throws IOException {
        long sizeA = Files.size(a);
        long sizeB = Files.size(b);
        if (sizeA != sizeB) {
            return false;
        }
        return Files.mismatch(a, b) == -1L;
    }
}