package delivery.portal.api;

import delivery.mobile.run.MobilePrecisionPickLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON feed for {@code /admin/mobile-precision}. In-memory only — a portal restart clears
 * the ring buffer; see {@link MobilePrecisionPickLog}.
 */
@RestController
@RequestMapping("/api/admin/mobile-precision")
public class MobilePrecisionDashboardController {

    private final MobilePrecisionPickLog log;

    public MobilePrecisionDashboardController(MobilePrecisionPickLog log) {
        this.log = log;
    }

    @GetMapping("/picks")
    public Map<String, Object> picks(@RequestParam(name = "limit", defaultValue = "200") int limit) {
        int safe = Math.min(Math.max(limit, 1), 500);
        List<MobilePrecisionPickLog.Entry> recent = log.recent(safe);
        List<Map<String, Object>> rows = new ArrayList<>(recent.size());
        int cursor = 0, fallback = 0, invalid = 0;
        for (MobilePrecisionPickLog.Entry e : recent) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("epochMs", e.epochMs());
            row.put("runId", e.runId());
            row.put("projectId", e.projectId());
            row.put("tenantId", e.tenantId());
            row.put("intent", e.intent());
            row.put("candidateCount", e.candidateCount());
            row.put("code", e.code());
            row.put("chosenId", e.chosenId());
            row.put("usedProvider", e.usedProvider());
            row.put("fallbackReason", e.fallbackReason());
            row.put("countsAsCursor", e.countsAsCursor());
            rows.add(row);
            if (!e.code().isEmpty()) {
                invalid++;
            } else if (e.countsAsCursor()) {
                cursor++;
            } else {
                fallback++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("picks", rows);
        out.put("totals", Map.of(
                "total", rows.size(),
                "cursorPicks", cursor,
                "keelFallbacks", fallback,
                "rejected", invalid));
        return out;
    }
}
