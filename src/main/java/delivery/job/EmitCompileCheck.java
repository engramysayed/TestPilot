package delivery.job;

import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Optional Maven smoke on the generated customer project before COMPLETED.
 * Uses {@code test-compile} so generated tests must resolve against page actions
 * (main-only {@code compile} missed Inventory over-merge bugs).
 * Flag: {@code delivery.emit-compile-check=true} (default true).
 */
public final class EmitCompileCheck {
    private EmitCompileCheck() {
    }

    public static void runIfEnabled(Path projectDir) throws Exception {
        runIfEnabled(projectDir, Duration.ofMinutes(10), () -> false);
    }

    public static void runIfEnabled(Path projectDir, Duration deadline, BooleanSupplier cancel) throws Exception {
        if (!enabled()) {
            LogsManager.info("EMIT_COMPILE_CHECK skipped (delivery.emit-compile-check=false)");
            return;
        }
        if (projectDir == null || !Files.isDirectory(projectDir)
                || !Files.isRegularFile(projectDir.resolve("pom.xml"))) {
            throw new IllegalStateException("EMIT_COMPILE_CHECK: missing pom.xml under " + projectDir);
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = new ProcessBuilder(
                windows ? "mvn.cmd" : "mvn",
                "-q",
                "-Dmaven.compiler.release=21",
                "test-compile");
        pb.directory(projectDir.toFile());
        ProcessSupervisor.Result result = ProcessSupervisor.run(
                pb, deadline == null ? Duration.ofMinutes(10) : deadline, cancel, 32_000);
        if (result.cancelled()) {
            throw new JobCancelledException();
        }
        if (result.timedOut()) {
            throw new IllegalStateException("EMIT_COMPILE_CHECK timed out after " + deadline);
        }
        if (!result.completedNormally()) {
            Path log = projectDir.resolve("docs/EMIT_COMPILE_FAIL.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log, result.output() == null ? "" : result.output());
            throw new IllegalStateException(
                    "EMIT_COMPILE_CHECK failed (test-compile) — see " + log.toAbsolutePath()
                            + " (exit=" + result.exitCode() + ")");
        }
        LogsManager.info("EMIT_COMPILE_CHECK ok (test-compile) for " + projectDir.getFileName());
    }

    static boolean enabled() {
        String p = System.getProperty("delivery.emit-compile-check");
        if (p == null || p.isBlank()) {
            p = System.getenv("DELIVERY_EMIT_COMPILE_CHECK");
        }
        if (p == null || p.isBlank()) {
            p = utils.PropertyReader.getProperty("delivery.emit-compile-check");
        }
        if (p == null || p.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(p.trim()) || "1".equals(p.trim());
    }
}
