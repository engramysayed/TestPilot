package delivery.packager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Post-emit Allure report: runs the generated framework's tests headless, then packages the Allure
 * HTML report as a side-car ZIP. Fail-open: a missing mvn, a hostile environment, a test crash or a
 * timeout all log and return {@link Result#generated()} = false without throwing.
 *
 * <p>Opt-in via system property {@code keel.framework.allure.autoreport=true} or env
 * {@code KEEL_FRAMEWORK_ALLURE_AUTOREPORT=true}. Default is off — generation can add minutes per
 * emit and needs a working browser.</p>
 */
public final class AllureReportBuilder {
    private static final Logger log = LoggerFactory.getLogger(AllureReportBuilder.class);

    public static final String PROP = "keel.framework.allure.autoreport";
    public static final String ENV = "KEEL_FRAMEWORK_ALLURE_AUTOREPORT";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(15);
    public static final Path RELATIVE_REPORT_DIR = Path.of("test-output", "target", "site", "allure-report");

    private AllureReportBuilder() {
    }

    public static boolean isEnabled() {
        String prop = System.getProperty(PROP);
        if (prop != null && !prop.isBlank()) {
            return parseBool(prop);
        }
        String env = System.getenv(ENV);
        if (env != null && !env.isBlank()) {
            return parseBool(env);
        }
        return true;
    }

    public static Result runAndPackage(Path projectDir, Path reportZipOut) {
        return runAndPackage(projectDir, reportZipOut, DEFAULT_TIMEOUT);
    }

    public static Result runAndPackage(Path projectDir, Path reportZipOut, Duration timeout) {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            return Result.skipped("project dir missing");
        }
        if (reportZipOut == null) {
            return Result.skipped("output path missing");
        }
        String mvn = findMvn();
        if (mvn == null) {
            return Result.skipped("mvn not on PATH");
        }
        Duration budget = (timeout == null || timeout.isZero() || timeout.isNegative())
                ? DEFAULT_TIMEOUT : timeout;
        try {
            int testExit = run(projectDir, budget, mvn, "-q", "clean", "test",
                    "-Dmaven.test.failure.ignore=true");
            log.info("[allure-report] mvn test exit={}", testExit);
            int reportExit = run(projectDir, budget, mvn, "-q",
                    "io.qameta.allure:allure-maven:report");
            if (reportExit != 0) {
                return Result.failed("allure-maven:report exit=" + reportExit);
            }
            Path reportDir = projectDir.resolve(RELATIVE_REPORT_DIR);
            if (!Files.isDirectory(reportDir)) {
                return Result.failed("report dir missing: " + reportDir);
            }
            zipDirectory(reportDir, reportZipOut);
            return Result.generated(reportZipOut);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("interrupted");
        } catch (IOException e) {
            return Result.failed(e.getMessage());
        }
    }

    private static int run(Path dir, Duration timeout, String... cmd)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[allure-report] {}", line);
            }
        }
        if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            p.destroyForcibly();
            throw new IOException("mvn timed out after " + timeout);
        }
        return p.exitValue();
    }

    static String findMvn() {
        String envPath = System.getenv("PATH");
        if (envPath == null) {
            return null;
        }
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String[] candidates = win ? new String[]{"mvn.cmd", "mvn.bat"} : new String[]{"mvn"};
        for (String part : envPath.split(File.pathSeparator)) {
            if (part == null || part.isBlank()) {
                continue;
            }
            for (String cmd : candidates) {
                Path candidate = Path.of(part, cmd);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return null;
    }

    private static void zipDirectory(Path source, Path zipOut) throws IOException {
        Files.createDirectories(zipOut.getParent());
        try (OutputStream fos = Files.newOutputStream(zipOut);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String rel = source.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(rel));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static boolean parseBool(String s) {
        return s != null && ("true".equalsIgnoreCase(s.trim()) || "1".equals(s.trim()));
    }

    public record Result(boolean generated, Path output, String reason) {
        public static Result generated(Path output) {
            return new Result(true, output, "");
        }
        public static Result skipped(String reason) {
            return new Result(false, null, reason);
        }
        public static Result failed(String reason) {
            return new Result(false, null, reason);
        }
    }
}
