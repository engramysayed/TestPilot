package delivery.portal.preflight;

import delivery.excel.KeelPathCounts;
import delivery.excel.ManualTestCase;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.ProjectCredentialService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the {@code GET /api/projects/{id}/preflight} body (ux-patches/BACKEND_NEEDS.md §1). */
@Service
public class ProjectPreflightService {
    private final GeneratedWorkbookService workbooks;
    private final ProjectCredentialService credentials;
    private final PreflightProbe probe;

    public ProjectPreflightService(GeneratedWorkbookService workbooks, ProjectCredentialService credentials,
                                   PreflightProbe probe) {
        this.workbooks = workbooks;
        this.credentials = credentials;
        this.probe = probe;
    }

    public Map<String, Object> run(String projectId, String baseUrl, String credentialProfile, String kind,
                                   boolean deep) throws Exception {
        String settings = "/projects/" + projectId + "?tab=settings";
        String casesHref = "/projects/" + projectId + "?tab=tcs";
        List<Map<String, Object>> problems = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        String url = baseUrl == null ? "" : baseUrl.trim();
        out.put("baseUrl", url.isEmpty() ? null : url);
        PreflightProbe.Result r;
        if (url.isEmpty()) {
            r = new PreflightProbe.Result(null, null, null, null);
            problems.add(problem("NO_BASE_URL", "This project has no website address yet.", settings));
        } else {
            r = probe.probe(url);
            if (Boolean.FALSE.equals(r.reachable())) {
                problems.add(problem("UNREACHABLE", r.message() == null ? "Keel could not open the website." : r.message(), settings));
            }
        }
        out.put("reachable", r.reachable());
        out.put("httpStatus", r.httpStatus());
        out.put("latencyMs", r.latencyMs());
        out.put("message", r.message());

        // login account (deep browser login check is not implemented yet; reported as not checked)
        List<ProjectCredentialService.CredentialSummary> creds = credentials.list(projectId);
        String profile = pickProfile(creds, credentialProfile);
        Map<String, Object> login = new LinkedHashMap<>();
        login.put("profile", profile);
        login.put("checked", false);
        login.put("ok", null);
        String loginMsg = null;
        if (credentialProfile != null && !credentialProfile.isBlank() && profile == null) {
            loginMsg = "There is no login account named \"" + credentialProfile.trim() + "\".";
            problems.add(problem("LOGIN_FAILED", loginMsg, settings));
        } else if (deep) {
            loginMsg = "The sign-in test is not available yet; only the account details were checked.";
        }
        login.put("message", loginMsg);
        out.put("login", login);

        List<ManualTestCase> cases = workbooks.hasWorkbook(projectId) ? workbooks.readCases(projectId) : List.of();
        KeelPathCounts counts = KeelPathCounts.from(cases);
        String k = kind == null ? "EXECUTE" : kind.trim().toUpperCase(Locale.ROOT);
        int runnable = "HUNT".equals(k) ? cases.size() : counts.automateRunnable();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("total", cases.size());
        c.put("runnable", runnable);
        c.put("manual", counts.get("MANUAL"));
        c.put("byPath", counts.toMap());
        out.put("cases", c);
        if (!"HUNT".equals(k)) {
            if (cases.isEmpty()) {
                problems.add(problem("NO_CASES", "This project has no test cases yet.", casesHref));
            } else if (runnable == 0) {
                problems.add(problem("NO_RUNNABLE_CASES", "All test cases are marked manual, so there is nothing to run.", casesHref));
            }
        }
        out.put("ok", problems.isEmpty());
        out.put("problems", problems);
        return out;
    }

    static String pickProfile(List<ProjectCredentialService.CredentialSummary> creds, String requested) {
        if (creds == null || creds.isEmpty()) {
            return null;
        }
        if (requested != null && !requested.isBlank()) {
            return creds.stream().map(ProjectCredentialService.CredentialSummary::profileName)
                    .filter(n -> n != null && n.equalsIgnoreCase(requested.trim())).findFirst().orElse(null);
        }
        return creds.stream().map(ProjectCredentialService.CredentialSummary::profileName)
                .filter("default"::equalsIgnoreCase).findFirst()
                .orElse(creds.get(0).profileName());
    }

    private static Map<String, Object> problem(String code, String message, String fixHref) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("code", code);
        p.put("message", message);
        p.put("fixHref", fixHref);
        return p;
    }
}
