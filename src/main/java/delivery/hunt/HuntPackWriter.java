package delivery.hunt;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class HuntPackWriter {
    private HuntPackWriter() {
    }

    public static Path writePack(Path huntRoot, HuntRequest request,
                                 String briefMd, String stopReason,
                                 List<Map<String, Object>> bugs,
                                 List<Map<String, Object>> scenarios,
                                 int cyclesUsed) throws IOException {
        return writePack(huntRoot, request, briefMd, stopReason, bugs, scenarios, cyclesUsed, "best-effort", 0);
    }

    public static Path writePack(Path huntRoot, HuntRequest request,
                                 String briefMd, String stopReason,
                                 List<Map<String, Object>> bugs,
                                 List<Map<String, Object>> scenarios,
                                 int cyclesUsed,
                                 String networkCapture) throws IOException {
        return writePack(huntRoot, request, briefMd, stopReason, bugs, scenarios, cyclesUsed, networkCapture, 0);
    }

    public static Path writePack(Path huntRoot, HuntRequest request,
                                 String briefMd, String stopReason,
                                 List<Map<String, Object>> bugs,
                                 List<Map<String, Object>> scenarios,
                                 int cyclesUsed,
                                 String networkCapture,
                                 int groundedRejectCount) throws IOException {
        return writePack(huntRoot, request, briefMd, stopReason, bugs, scenarios, cyclesUsed,
                networkCapture, groundedRejectCount, List.of(), 0, 0);
    }

    public static Path writePack(Path huntRoot, HuntRequest request,
                                 String briefMd, String stopReason,
                                 List<Map<String, Object>> bugs,
                                 List<Map<String, Object>> scenarios,
                                 int cyclesUsed,
                                 String networkCapture,
                                 int groundedRejectCount,
                                 List<String> strategiesCompleted,
                                 int oracleBugCount) throws IOException {
        return writePack(huntRoot, request, briefMd, stopReason, bugs, scenarios, cyclesUsed,
                networkCapture, groundedRejectCount, strategiesCompleted, oracleBugCount, 0);
    }

    public static Path writePack(Path huntRoot, HuntRequest request,
                                 String briefMd, String stopReason,
                                 List<Map<String, Object>> bugs,
                                 List<Map<String, Object>> scenarios,
                                 int cyclesUsed,
                                 String networkCapture,
                                 int groundedRejectCount,
                                 List<String> strategiesCompleted,
                                 int oracleBugCount,
                                 int coverageUrlCount) throws IOException {
        Files.createDirectories(huntRoot);
        Files.writeString(huntRoot.resolve("brief.md"), briefMd == null ? "" : briefMd, StandardCharsets.UTF_8);

        List<Map<String, Object>> bugRows = bugs == null ? List.of() : bugs;
        List<Map<String, Object>> scenarioRows = scenarios == null ? List.of() : scenarios;

        String summary = buildSummary(request, stopReason, cyclesUsed, bugRows.size(), scenarioRows.size(),
                networkCapture == null ? "best-effort" : networkCapture, groundedRejectCount,
                strategiesCompleted, oracleBugCount, coverageUrlCount);
        Files.writeString(huntRoot.resolve("SUMMARY.md"), summary, StandardCharsets.UTF_8);

        Map<String, Object> bugReport = new LinkedHashMap<>();
        bugReport.put("jobId", request.getJobId());
        bugReport.put("projectId", request.getProjectId());
        bugReport.put("kind", "HUNT");
        bugReport.put("rows", toBugReportRows(bugRows));
        Files.writeString(huntRoot.resolve("bug-report.json"),
                new org.json.JSONObject(bugReport).toString(2), StandardCharsets.UTF_8);
        Files.writeString(huntRoot.resolve("bug-report.csv"), toBugCsv(bugRows), StandardCharsets.UTF_8);

        Map<String, Object> candidates = new LinkedHashMap<>();
        candidates.put("jobId", request.getJobId());
        candidates.put("scenarioCap", request.getScenarioCap());
        candidates.put("scenarios", scenarioRows);
        Files.writeString(huntRoot.resolve("candidate-scenarios.json"),
                new org.json.JSONObject(candidates).toString(2), StandardCharsets.UTF_8);
        Files.writeString(huntRoot.resolve("candidate-scenarios.csv"),
                toScenarioCsv(scenarioRows), StandardCharsets.UTF_8);

        Path zipPath = huntRoot.resolve("hunter-pack.zip");
        zipDirectory(huntRoot, zipPath, "hunter-pack.zip");
        return zipPath;
    }

    static String buildSummary(HuntRequest request, String stopReason, int cyclesUsed,
                               int bugCount, int scenarioCount, String networkCapture,
                               int groundedRejectCount) {
        return buildSummary(request, stopReason, cyclesUsed, bugCount, scenarioCount,
                networkCapture, groundedRejectCount, List.of(), 0, 0);
    }

    static String buildSummary(HuntRequest request, String stopReason, int cyclesUsed,
                               int bugCount, int scenarioCount, String networkCapture,
                               int groundedRejectCount,
                               List<String> strategiesCompleted,
                               int oracleBugCount) {
        return buildSummary(request, stopReason, cyclesUsed, bugCount, scenarioCount,
                networkCapture, groundedRejectCount, strategiesCompleted, oracleBugCount, 0);
    }

    static String buildSummary(HuntRequest request, String stopReason, int cyclesUsed,
                               int bugCount, int scenarioCount, String networkCapture,
                               int groundedRejectCount,
                               List<String> strategiesCompleted,
                               int oracleBugCount,
                               int coverageUrlCount) {
        String strategiesLine = strategiesCompleted == null || strategiesCompleted.isEmpty()
                ? "none"
                : String.join(", ", strategiesCompleted);
        return """
                # Bug Hunter summary

                - Job: `%s`
                - Project: `%s`
                - Planner: %s
                - DOM mode: %s
                - Stop reason: %s
                - Cycles used: %d / %d
                - Action cap / cycle: %d
                - Bugs: %d (oracle: %d)
                - Candidate scenarios: %d (cap %d)
                - Strategies completed: %s
                - Coverage URLs visited: %d
                - Grounded locator rejects: %d
                - Network capture: %s
                """.formatted(
                nullSafe(request.getJobId()),
                nullSafe(request.getProjectId()),
                nullSafe(request.getPlanner()),
                nullSafe(request.getDomMode()),
                nullSafe(stopReason),
                cyclesUsed,
                request.getCycleCeiling(),
                request.getActionCapPerCycle(),
                bugCount,
                oracleBugCount,
                scenarioCount,
                request.getScenarioCap(),
                strategiesLine,
                coverageUrlCount,
                groundedRejectCount,
                nullSafe(networkCapture)
        );
    }

    private static List<Map<String, Object>> toBugReportRows(List<Map<String, Object>> bugs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> bug : bugs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tcId", "HUNT_" + String.format(java.util.Locale.ROOT, "%02d", i++));
            row.put("title", str(bug.get("title")));
            row.put("qaStatus", "FAIL");
            row.put("failureReasonUser", str(bug.get("actual")));
            row.put("failureReason", str(bug.get("actual")));
            row.put("severity", str(bug.get("severity")));
            row.put("repro", str(bug.get("repro")));
            row.put("expected", str(bug.get("expected")));
            row.put("blockerStepIndex", -1);
            row.put("blockerIntent", "");
            row.put("visualAssertStatus", "");
            row.put("designCompareStatus", "");
            row.put("screenshotUrls", List.of());
            rows.add(row);
        }
        return rows;
    }

    private static String toBugCsv(List<Map<String, Object>> bugs) {
        StringBuilder sb = new StringBuilder();
        sb.append("tcId,title,qaStatus,failureReasonUser,failureReason,severity,repro,expected\n");
        int i = 1;
        for (Map<String, Object> bug : bugs) {
            sb.append(csv("HUNT_" + String.format(java.util.Locale.ROOT, "%02d", i++))).append(',')
                    .append(csv(str(bug.get("title")))).append(',')
                    .append("FAIL").append(',')
                    .append(csv(str(bug.get("actual")))).append(',')
                    .append(csv(str(bug.get("actual")))).append(',')
                    .append(csv(str(bug.get("severity")))).append(',')
                    .append(csv(str(bug.get("repro")))).append(',')
                    .append(csv(str(bug.get("expected")))).append('\n');
        }
        return sb.toString();
    }

    private static String toScenarioCsv(List<Map<String, Object>> scenarios) {
        StringBuilder sb = new StringBuilder();
        sb.append("title,steps,expected,tags\n");
        for (Map<String, Object> sc : scenarios) {
            sb.append(csv(str(sc.get("title")))).append(',')
                    .append(csv(str(sc.get("steps")))).append(',')
                    .append(csv(str(sc.get("expected")))).append(',')
                    .append(csv(str(sc.get("tags")))).append('\n');
        }
        return sb.toString();
    }

    private static void zipDirectory(Path root, Path zipPath, String skipName) throws IOException {
        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos);
             Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals(skipName))
                    .forEach(p -> {
                        try {
                            String entry = root.relativize(p).toString().replace('\\', '/');
                            zos.putNextEntry(new ZipEntry(entry));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static String csv(String s) {
        String v = s == null ? "" : s.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
