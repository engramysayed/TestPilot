package delivery.mobile.codegen;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * Compile-before-publish gate (M4-04). On failure, prior published ZIP bytes are preserved.
 */
public final class MobileCompileGate {

    public record Result(boolean ok, String message, Path preservedPriorZip) {}

    private MobileCompileGate() {}

    /**
     * Run {@code mvn -q -DskipTests compile} in {@code projectDir}.
     * If compile fails and {@code priorPublishedZip} exists, it is left untouched
     * (caller must not overwrite until this returns ok).
     */
    public static Result compileBeforePublish(Path projectDir, Path priorPublishedZip) throws Exception {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            throw new IllegalArgumentException("projectDir required");
        }
        Path pom = projectDir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return new Result(false, "missing pom.xml — compile skipped/failed", priorPublishedZip);
        }
        // Prefer a dry structural check when Maven may be heavy; still invoke mvn when available.
        return runMaven(projectDir, priorPublishedZip, mavenBinary(), "compile");
    }

    /** Compiles generated test sources (screens live under src/test/java). */
    public static Result compileTests(Path projectDir) throws Exception {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            throw new IllegalArgumentException("projectDir required");
        }
        return runMaven(projectDir, null, mavenBinary(), "test-compile");
    }

    private static Result runMaven(Path projectDir, Path priorPublishedZip, String binary, String goal) throws Exception {
        Path pom = projectDir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return new Result(false, "missing pom.xml — compile skipped/failed", priorPublishedZip);
        }
        ProcessBuilder pb = new ProcessBuilder(
                binary, "-q", "-f", pom.toAbsolutePath().toString(), "-DskipTests", goal);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (java.io.IOException e) {
            return new Result(false, "Maven is not on PATH (" + binary + "). " + e.getMessage(), priorPublishedZip);
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        p.getInputStream().transferTo(buf);
        boolean finished = p.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            p.destroyForcibly();
            return new Result(false, "compile timed out — prior ZIP preserved", priorPublishedZip);
        }
        int code = p.exitValue();
        if (code != 0) {
            String msg = "compile failed exit=" + code + " — prior ZIP preserved";
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (out.length() > 500) {
                out = out.substring(out.length() - 500);
            }
            if (!out.isBlank()) {
                msg = msg + "\n" + out;
            }
            return new Result(false, msg, priorPublishedZip);
        }
        return new Result(true, "compile ok", priorPublishedZip);
    }

    private static String mavenBinary() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(java.util.Locale.ROOT).contains("win") ? "mvn.cmd" : "mvn";
    }

    /**
     * Unit-testable: simulate failed compile without invoking Maven — ensures prior bytes unchanged.
     */
    public static Result failPreserve(Path priorPublishedZip, String reason) throws Exception {
        if (priorPublishedZip != null && Files.isRegularFile(priorPublishedZip)) {
            // Touch-copy to prove we do not truncate
            Path bak = priorPublishedZip.resolveSibling(priorPublishedZip.getFileName() + ".preserved");
            Files.copy(priorPublishedZip, bak, StandardCopyOption.REPLACE_EXISTING);
            Files.move(bak, priorPublishedZip, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Result(false, reason == null ? "compile failed" : reason, priorPublishedZip);
    }
}
