package delivery.visual;

import org.json.JSONObject;

import java.nio.file.Path;

public record VisualDiffCompareResult(
        VisualDiffStatus status,
        String caseKey,
        String viewport,
        String runId,
        double changedFraction,
        int changedBlocks,
        int totalBlocks,
        double failThreshold,
        String message,
        Path actualPath,
        Path baselinePath,
        Path resultPath
) {
    public boolean failed() {
        return status == VisualDiffStatus.FAIL || status == VisualDiffStatus.ERROR;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("status", status == null ? "ERROR" : status.name());
        o.put("caseKey", caseKey == null ? "" : caseKey);
        o.put("viewport", viewport == null ? "" : viewport);
        o.put("runId", runId == null ? "" : runId);
        o.put("changedFraction", changedFraction);
        o.put("changedBlocks", changedBlocks);
        o.put("totalBlocks", totalBlocks);
        o.put("failThreshold", failThreshold);
        o.put("message", message == null ? "" : message);
        if (actualPath != null) {
            o.put("actualPath", actualPath.toString().replace('\\', '/'));
        }
        if (baselinePath != null) {
            o.put("baselinePath", baselinePath.toString().replace('\\', '/'));
        }
        return o;
    }
}
