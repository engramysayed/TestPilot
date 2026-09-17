package delivery.portal.api;

import delivery.job.BudgetLedger;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/usage")
public class UsageController {
    private final PortalStore store;
    private final CurrentUserService currentUser;

    public UsageController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public record LimitsRequest(Long hardCapUnits, Long warningUnits) {
    }

    @GetMapping
    public ResponseEntity<?> history(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(toBody(store.budgetSnapshot(projectId)));
    }

    @PutMapping
    public ResponseEntity<?> updateLimits(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) LimitsRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        if (body == null || body.hardCapUnits() == null || body.warningUnits() == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "hardCapUnits and warningUnits are required").asMap());
        }
        return ResponseEntity.ok(toBody(store.updateBudgetLimits(
                projectId, body.hardCapUnits(), body.warningUnits())));
    }

    private static Map<String, Object> toBody(BudgetLedger.Snapshot snap) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BudgetLedger.UsageRecord rec : snap.usage()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", rec.jobId());
            row.put("estimatedUnits", rec.estimatedUnits());
            row.put("reconciledUnits", rec.reconciledUnits());
            row.put("estimatedKind", rec.estimatedKind().name());
            row.put("reconciledKind", rec.reconciledKind().name());
            row.put("at", rec.at());
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reservedUnits", snap.reservedUnits());
        body.put("hardCapUnits", snap.hardCapUnits());
        body.put("warningUnits", snap.warningUnits());
        body.put("warning", snap.warningUnits() > 0 && snap.reservedUnits() >= snap.warningUnits());
        body.put("usage", rows);
        return body;
    }
}
