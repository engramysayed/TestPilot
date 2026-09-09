package delivery.job;

import delivery.authoring.LoginStepDetector;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.CodegenNaming;
import delivery.codegen.CodeWriter;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 3 (static): compare Excel intents vs proven/emitted steps and annotate residual defects.
 * Does not re-open the browser.
 */
public class RevisePhase {

    /**
     * When {@code delivery.honesty-demote=true}, demote PASSED drafts that fail honesty checks
     * before codegen so TODO/PARTIAL emit is honest.
     */
    public static List<TcDraft> applyHonestyDemote(List<TcDraft> drafts, List<ManualTestCase> allCases) {
        return applyHonestyDemote(drafts, allCases, SemanticPassGate.honestyDemoteEnabled());
    }

    /** Same as {@link #applyHonestyDemote} but can force on for client-delivery final revise. */
    public static List<TcDraft> applyHonestyDemoteForced(List<TcDraft> drafts, List<ManualTestCase> allCases) {
        return applyHonestyDemote(drafts, allCases, true);
    }

    static List<TcDraft> applyHonestyDemote(
            List<TcDraft> drafts, List<ManualTestCase> allCases, boolean enabled) {
        if (!enabled || drafts == null) {
            return drafts == null ? List.of() : drafts;
        }
        Map<String, ManualTestCase> byId = allCases == null ? Map.of() : allCases.stream()
                .collect(Collectors.toMap(ManualTestCase::tcId, tc -> tc, (a, b) -> a));
        List<TcDraft> out = new ArrayList<>();
        for (TcDraft d : drafts) {
            if (d.status() != TcDraftStatus.PASSED) {
                out.add(d);
                continue;
            }
            List<String> issues = findIssues(d, byId.get(d.tcId()));
            if (issues.isEmpty()) {
                out.add(d);
                continue;
            }
            String reason = "HONESTY_GATE: " + String.join("; ", issues);
            out.add(new TcDraft(
                    d.tcId(), d.title(), d.stepsText(), d.expectedResult(),
                    TcDraftStatus.PARTIAL, d.provenSteps(), d.loginSteps(),
                    d.needsLoginBeforeMethod(), d.blockerStepIndex(),
                    d.blockerIntent().isBlank() ? "honesty-gate" : d.blockerIntent(),
                    reason, d.evidenceDir(), d.retryCountOnBlocker(), d.lastPageUrl(),
                    d.healTier(), d.healSkipReason(), d.loginFormUrl()));
        }
        return out;
    }

    public void revise(Path projectDir, List<TcDraft> drafts, List<ManualTestCase> allCases)
            throws Exception {
        Map<String, ManualTestCase> byId = allCases.stream()
                .collect(Collectors.toMap(ManualTestCase::tcId, tc -> tc, (a, b) -> a));
        List<String> notes = new ArrayList<>();

        for (TcDraft d : drafts) {
            if (d.status() == TcDraftStatus.REUSED) {
                continue;
            }
            ManualTestCase tc = byId.get(d.tcId());
            List<String> issues = findIssues(d, tc);
            if (issues.isEmpty()) {
                continue;
            }
            notes.add("## " + d.tcId() + " (" + d.status() + ")");
            for (String issue : issues) {
                notes.add("- " + issue);
            }
            notes.add("");
            annotateTestFile(projectDir, d, issues);
        }

        Path docs = projectDir.resolve("docs");
        Files.createDirectories(docs);
        StringBuilder md = new StringBuilder();
        md.append("# REVISE NOTES (Phase 3 — static)\n\n");
        md.append("Static check of Excel intents vs proven/emitted steps. ");
        md.append("No browser heal was run.\n\n");
        if (notes.isEmpty()) {
            md.append("No residual defects detected.\n");
        } else {
            md.append(String.join("\n", notes));
        }
        Files.writeString(docs.resolve("REVISE_NOTES.md"), md.toString(), StandardCharsets.UTF_8);
    }

    static List<String> findIssues(TcDraft d, ManualTestCase tc) {
        List<String> issues = new ArrayList<>();
        if (d.status() == TcDraftStatus.TODO || d.status() == TcDraftStatus.PARTIAL) {
            if (d.failureReason() != null && !d.failureReason().isBlank()) {
                issues.add("Blocked: " + d.failureReason());
            }
        }
        if (tc == null) {
            return issues;
        }
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.bodyIntents(
                tc, LoginStepDetector.hasLoginSteps(tc) && !LoginStepDetector.isLoginFailureCase(tc));
        List<ProvenStep> proven = d.provenSteps() == null ? List.of() : d.provenSteps();
        if (d.status() == TcDraftStatus.PASSED && proven.size() < intents.size()) {
            issues.add("PASSED but proven steps (" + proven.size() + ") < Excel body intents ("
                    + intents.size() + ")");
        }
        if (d.status() == TcDraftStatus.PARTIAL || d.status() == TcDraftStatus.TODO) {
            int blocker = d.blockerStepIndex();
            if (blocker >= 0 && blocker < intents.size()) {
                for (int i = blocker; i < intents.size(); i++) {
                    issues.add("Remaining Excel intent: " + trim(intents.get(i).text(), 120));
                }
            } else if (proven.size() < intents.size()) {
                for (int i = proven.size(); i < intents.size(); i++) {
                    issues.add("Remaining Excel intent: " + trim(intents.get(i).text(), 120));
                }
            }
        }
        long distinctPages = proven.stream()
                .map(ProvenStep::pageName)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .count();
        if (proven.size() >= 6 && distinctPages <= 1) {
            issues.add("All proven steps share a single page name — check per-step URL stamping");
        }
        return issues;
    }

    private static void annotateTestFile(Path projectDir, TcDraft d, List<String> issues) throws Exception {
        boolean passed = d.status() == TcDraftStatus.PASSED || d.status() == TcDraftStatus.REUSED;
        String className = CodegenNaming.testClassName(d.tcId(), passed);
        Path file = projectDir.resolve(passed
                ? "src/test/java/project/tests/generated/" + className + ".java"
                : "src/test/java/project/tests/todo/" + className + ".java");
        if (!Files.exists(file)) {
            return;
        }
        String src = Files.readString(file, StandardCharsets.UTF_8);
        StringBuilder inject = new StringBuilder();
        for (String issue : issues) {
            inject.append("        // REVIEW: ").append(issue.replace("\n", " ")).append('\n');
        }
        String marker = "// STOPPED HERE:";
        int idx = src.indexOf(marker);
        if (idx >= 0) {
            src = src.substring(0, idx) + inject + src.substring(idx);
        } else {
            int run = src.indexOf("public void runCase()");
            if (run < 0) {
                return;
            }
            int brace = src.indexOf('{', run);
            if (brace < 0) {
                return;
            }
            src = src.substring(0, brace + 1) + "\n" + inject + src.substring(brace + 1);
        }
        Files.writeString(file, src, StandardCharsets.UTF_8);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
