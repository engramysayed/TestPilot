package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.excel.RequirementTrace;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Story → test case coverage for the project library (from {@code req:<id>} tags). */
@RestController
public class RequirementCoverageController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final GeneratedWorkbookService workbooks;

    public RequirementCoverageController(PortalStore store, CurrentUserService currentUser,
                                         GeneratedWorkbookService workbooks) {
        this.store = store;
        this.currentUser = currentUser;
        this.workbooks = workbooks;
    }

    @GetMapping("/api/projects/{projectId}/requirements-coverage")
    public ResponseEntity<?> coverage(@PathVariable String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) return denied;
        List<ManualTestCase> cases;
        try {
            cases = workbooks.readCases(projectId);
        } catch (Exception e) {
            cases = List.of();
        }
        Map<String, List<String>> matrix = RequirementTrace.matrix(cases);
        List<Map<String, Object>> rows = new ArrayList<>();
        matrix.forEach((ref, ids) -> {
            if (!ref.isEmpty()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("requirement", ref);
                r.put("caseCount", ids.size());
                r.put("tcIds", ids);
                rows.add(r);
            }
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("requirements", rows);
        out.put("untracedTcIds", matrix.getOrDefault("", List.of()));
        out.put("caseCount", cases.size());
        return ResponseEntity.ok(out);
    }
}
