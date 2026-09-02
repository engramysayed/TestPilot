package delivery.portal.service;

import delivery.ir.TcDraft;
import delivery.portal.model.JobRecord;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BugReportService {
    private static final List<String> CSV_HEADERS = List.of(
            "tcId",
            "title",
            "qaStatus",
            "failureReasonUser",
            "failureReason",
            "blockerStepIndex",
            "blockerIntent",
            "visualAssertStatus",
            "designCompareStatus",
            "screenshotUrls"
    );

    private final ExecuteRunService executeRuns;

    public BugReportService(ExecuteRunService executeRuns) {
        this.executeRuns = executeRuns;
    }

    public Optional<Map<String, Object>> buildReport(String jobId, Long ownerUserId) throws Exception {
        Optional<JobRecord> job = executeRuns.requireOwnedExecuteJob(jobId, ownerUserId);
        if (job.isEmpty()) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TcDraft draft : executeRuns.readDraftsForJob(jobId)) {
            if (!executeRuns.includeInBugReport(job.get(), draft)) {
                continue;
            }
            rows.add(executeRuns.failReportRow(job.get(), draft));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("jobId", jobId);
        report.put("rows", rows);
        return Optional.of(report);
    }

    public String toCsv(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", CSV_HEADERS)).append('\n');
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.getOrDefault("rows", List.of());
        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>();
            for (String header : CSV_HEADERS) {
                Object value = row.get(header);
                if ("screenshotUrls".equals(header) && value instanceof List<?> list) {
                    cells.add(csvCell(list.stream().map(Object::toString).collect(Collectors.joining(";"))));
                } else {
                    cells.add(csvCell(value));
                }
            }
            sb.append(String.join(",", cells)).append('\n');
        }
        return sb.toString();
    }

    static String csvCell(Object value) {
        String s = value == null ? "" : value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static Optional<String> readEvidenceStatus(Path evidenceDir, String jsonFileName) {
        if (evidenceDir == null) {
            return Optional.empty();
        }
        Path json = evidenceDir.resolve(jsonFileName);
        if (!Files.isRegularFile(json)) {
            return Optional.empty();
        }
        try {
            JSONObject root = new JSONObject(Files.readString(json));
            String status = root.optString("status", "").trim();
            return status.isEmpty() ? Optional.empty() : Optional.of(status);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
