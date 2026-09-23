package delivery.portal.service;

import delivery.portal.model.JobRecord;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JobLinkEnricher {
    private final ExecuteRunService executeRunService;

    public JobLinkEnricher(ExecuteRunService executeRunService) {
        this.executeRunService = executeRunService;
    }

    public void enrich(Map<String, Object> row, String jobId, JobRecord.JobKind kind, String status) {
        row.put("statusUrl", "/status?jobId=" + jobId);
        if (kind != JobRecord.JobKind.EXECUTE) {
            return;
        }
        String resultsUrl = "/execute?jobId=" + jobId;
        row.put("resultsUrl", resultsUrl);
        row.put("evidenceUrl", "/evidence?jobId=" + jobId);
        if (!isTerminalExecute(status)) {
            return;
        }
        try {
            Optional<String> failedTc = executeRunService.findPrimaryFailedTcId(jobId);
            if (failedTc.isPresent()) {
                row.put("primaryTcId", failedTc.get());
                String deep = resultsUrl + "&tcId=" + failedTc.get();
                row.put("resultsUrl", deep);
                row.put("evidenceUrl", "/evidence?jobId=" + jobId + "&tcId=" + failedTc.get());
            }
        } catch (Exception ignored) {
            // Keep base URLs when IR is unavailable.
        }
    }

    public Map<String, Object> baseRow(String jobId, String projectId, String jobKind, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", jobId);
        row.put("projectId", projectId);
        row.put("jobKind", jobKind);
        row.put("status", status);
        enrich(row, jobId, JobRecord.parseJobKind(jobKind), status);
        return row;
    }

    private static boolean isTerminalExecute(String status) {
        return "COMPLETED".equals(status)
                || "COMPLETED_WITH_BLOCK".equals(status)
                || "FAILED".equals(status);
    }
}
