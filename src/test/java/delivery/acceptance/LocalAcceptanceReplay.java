package delivery.acceptance;

import delivery.job.ProcessSupervisor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ZIP replay runner: ProcessSupervisor drain/deadline/tree-kill instead of blocking stdout reads.
 */
public final class LocalAcceptanceReplay {
    private static final Pattern TEST_METHOD = Pattern.compile(
            "<test-method(?![^>]*is-config)[^>]*name=\"([^\"]+)\"[^>]*status=\"([^\"]+)\"");
    private static final int MAX_OUTPUT_CHARS = 2_000_000;

    public record MavenRun(
            int exitCode,
            Map<String, String> statuses,
            String log,
            boolean timedOut,
            boolean cancelled,
            boolean alive
    ) {
        public MavenRun {
            statuses = statuses == null ? Map.of() : Map.copyOf(statuses);
            log = log == null ? "" : log;
        }

        public MavenRun withStatuses(Map<String, String> next) {
            return new MavenRun(exitCode, next, log, timedOut, cancelled, alive);
        }
    }

    private LocalAcceptanceReplay() {
    }

    public static MavenRun run(ProcessBuilder builder, Duration deadline, Path logFile) throws Exception {
        ProcessSupervisor.Result result = ProcessSupervisor.run(
                builder, deadline == null ? Duration.ofMinutes(20) : deadline, () -> false, MAX_OUTPUT_CHARS);
        String output = result.output() == null ? "" : result.output();
        if (logFile != null) {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, output, StandardCharsets.UTF_8);
        }
        return new MavenRun(
                result.exitCode(), Map.of(), output, result.timedOut(), result.cancelled(), result.alive());
    }

    public static Map<String, String> parseStatuses(Path xml) throws Exception {
        Map<String, String> statuses = new LinkedHashMap<>();
        if (xml == null || !Files.isRegularFile(xml)) {
            return statuses;
        }
        Matcher simple = TEST_METHOD.matcher(Files.readString(xml, StandardCharsets.UTF_8));
        while (simple.find()) {
            statuses.put(simple.group(1), simple.group(2));
        }
        return statuses;
    }

    public static MavenRun attachReports(MavenRun run, Path projectDir) throws Exception {
        Map<String, String> statuses = new LinkedHashMap<>();
        if (projectDir != null) {
            statuses.putAll(parseStatuses(projectDir.resolve("test-output/target/surefire-reports/testng-results.xml")));
            statuses.putAll(parseStatuses(projectDir.resolve("target/surefire-reports/testng-results.xml")));
        }
        return run.withStatuses(statuses);
    }
}
