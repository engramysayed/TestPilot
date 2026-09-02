package delivery.job;

import utils.LogsManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        boolean finished = proc.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            proc.destroyForcibly();
            throw new IllegalStateException("EMIT_COMPILE_CHECK timed out after 10m");
        }
        if (proc.exitValue() != 0) {
            Path log = projectDir.resolve("docs/EMIT_COMPILE_FAIL.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log, out.toString(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    "EMIT_COMPILE_CHECK failed (test-compile) — see " + log.toAbsolutePath()
                            + " (exit=" + proc.exitValue() + ")");
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
            return true; // default on after Phase A
        }
        return "true".equalsIgnoreCase(p.trim()) || "1".equals(p.trim());
    }
}
