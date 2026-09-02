package delivery.portal.service;

import delivery.authoring.LoginStepDetector;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads durable conversion drafts from the project store for the project detail UI.
 */
@Service
public class ProjectTcService {
    private final PortalStore store;

    public ProjectTcService(PortalStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> listTcs(String projectId) throws Exception {
        String runAt = conversionRunLabel(projectId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TcDraft d : readDrafts(projectId)) {
            Map<String, Object> row = summary(projectId, d);
            row.put("runAtLabel", runAt);
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> getTc(String projectId, String tcId) throws Exception {
        TcDraftStore drafts = draftStore(projectId);
        if (!Files.isDirectory(drafts.irDir())) {
            throw new IllegalArgumentException("No IR store for project");
        }
        TcDraft d = drafts.read(tcId);
        Map<String, Object> detail = summary(projectId, d);
        detail.put("stepsText", d.stepsText());
        detail.put("expectedResult", d.expectedResult());
        detail.put("lastPageUrl", d.lastPageUrl());
        detail.put("runAtLabel", conversionRunLabel(projectId));
        List<Map<String, Object>> timeline = buildTimeline(projectId, d);
        attachExistingScreenshots(projectId, d.tcId(), timeline);
        detail.put("timeline", timeline);
        return detail;
    }

    /** Drop screenshot URLs that 404 so the UI does not show broken &lt;img&gt; icons. */
    private void attachExistingScreenshots(String projectId, String tcId, List<Map<String, Object>> timeline) {
        for (Map<String, Object> row : timeline) {
            String fileName = null;
            Object shot = row.get("screenshot");
            if (shot != null && !shot.toString().isBlank()) {
                fileName = shot.toString();
            } else if ("failed".equals(row.get("state"))) {
                fileName = "failure.png";
            }
            row.remove("screenshotUrl");
            if (fileName == null) {
                continue;
            }
            if (resolveScreenshot(projectId, tcId, fileName).isPresent()) {
                row.put("screenshot", fileName);
                row.put("screenshotUrl", "/api/projects/" + projectId
                        + "/tcs/" + tcId + "/screenshots/" + fileName + "?v=" + fileName);
            } else {
                row.remove("screenshot");
            }
        }
    }

    public String conversionRunLabel(String projectId) {
        return store.lastActivity(projectId)
                .map(WHEN::format)
                .orElse("Unknown");
    }

    private static final java.time.format.DateTimeFormatter WHEN =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(java.time.ZoneId.systemDefault());

    public Optional<Path> resolveScreenshot(String projectId, String tcId, String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Optional.empty();
        }
        Path file = store.filesystemStoreFor(projectId).projectRoot(projectId)
                .resolve("evidence").resolve(tcId).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private List<TcDraft> readDrafts(String projectId) throws Exception {
        TcDraftStore drafts = draftStore(projectId);
        if (!Files.isDirectory(drafts.irDir())) {
            return List.of();
        }
        return drafts.readAll();
    }

    private TcDraftStore draftStore(String projectId) {
        Path root = store.filesystemStoreFor(projectId).projectRoot(projectId);
        return new TcDraftStore(root);
    }

    private static Map<String, Object> summary(String projectId, TcDraft d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tcId", d.tcId());
        m.put("title", d.title());
        m.put("status", d.status().name());
        m.put("provenCount", d.provenSteps().size());
        m.put("blockerStepIndex", d.blockerStepIndex());
        m.put("blockerIntent", d.blockerIntent());
        m.put("failureReason", d.failureReason());
        m.put("needsLoginBeforeMethod", d.needsLoginBeforeMethod());
        // All statuses can expand to show proven steps (+ remaining for TODO/PARTIAL)
        m.put("expandable", true);
        return m;
    }

    static List<Map<String, Object>> buildTimeline(String projectId, TcDraft d) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        int i = 0;
        for (ProvenStep s : d.provenSteps()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i);
            row.put("state", "passed");
            row.put("label", labelFor(s));
            row.put("pageName", s.pageName());
            row.put("action", s.action());
            row.put("locator", formatLocator(s));
            row.put("rationale", s.rationale());
            String shot = s.screenshotRelPath();
            if (shot != null && !shot.isBlank()) {
                row.put("screenshot", shot);
                // URL attached in getTc only when the file exists (avoids broken <img>)
            }
            timeline.add(row);
            i++;
        }
        if (d.status() == TcDraftStatus.PARTIAL || d.status() == TcDraftStatus.TODO) {
            if (d.failureReason() != null && !d.failureReason().isBlank()) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("index", d.blockerStepIndex() >= 0 ? d.blockerStepIndex() : i);
                fail.put("state", "failed");
                fail.put("label", d.blockerIntent() == null || d.blockerIntent().isBlank()
                        ? "Blocked" : d.blockerIntent());
                fail.put("reason", FailureReasonHumanizer.forUser(d.failureReason()));
                fail.put("reasonTechnical", d.failureReason());
                // screenshotUrl attached in getTc only when failure.png exists on disk
                timeline.add(fail);
            }
            ManualTestCase synthetic = new ManualTestCase(
                    d.tcId(), d.title(), "", d.stepsText(), d.expectedResult(), "", "");
            List<StepIntentBinder.IntentLine> intents = StepIntentBinder.bodyIntents(
                    synthetic,
                    LoginStepDetector.hasLoginSteps(synthetic)
                            && !LoginStepDetector.isLoginFailureCase(synthetic));
            int start = d.blockerStepIndex() >= 0 ? d.blockerStepIndex() : d.provenSteps().size();
            int remainFrom = Math.min(start + 1, intents.size());
            if (d.blockerStepIndex() < 0) {
                remainFrom = d.provenSteps().size();
            }
            for (int r = remainFrom; r < intents.size(); r++) {
                Map<String, Object> rem = new LinkedHashMap<>();
                rem.put("index", r);
                rem.put("state", "remaining");
                rem.put("label", intents.get(r).text());
                rem.put("kind", intents.get(r).kind().name());
                timeline.add(rem);
            }
        }
        return timeline;
    }

    /** Test helper without projectId for URL building. */
    static List<Map<String, Object>> buildTimeline(TcDraft d) {
        return buildTimeline("prj_test", d);
    }

    private static String labelFor(ProvenStep s) {
        String action = s.action() == null ? "" : s.action();
        if (s.assertionType() != null && !s.assertionType().isBlank()) {
            String expected = s.assertionExpected();
            if (expected != null && !expected.isBlank()) {
                return "assert " + s.assertionType() + " \"" + expected + "\"";
            }
            return "assert " + s.assertionType() + " " + nullToEmpty(s.locatorValue());
        }
        if ("type".equalsIgnoreCase(action) || "select".equalsIgnoreCase(action)) {
            String value = s.value();
            if (value != null && !value.isBlank()) {
                return action + " " + nullToEmpty(s.locatorValue()) + " = " + value;
            }
            return action + " " + nullToEmpty(s.locatorValue());
        }
        return action + " " + nullToEmpty(s.locatorValue());
    }

    private static String formatLocator(ProvenStep s) {
        if (s.locatorValue() == null || s.locatorValue().isBlank()) {
            return "";
        }
        return nullToEmpty(s.locatorStrategy()) + ":" + s.locatorValue();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
