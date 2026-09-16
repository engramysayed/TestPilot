package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emit-time Call-before: copy prerequisite proven steps into the leaf setup chain,
 * and refuse to advertise a leaf as PASSED when a prerequisite is not reusable proof.
 */
public final class CallBeforeSetup {
    public static final String BLOCKED_PREFIX = "CALL_BEFORE_BLOCKED:";

    private CallBeforeSetup() {
    }

    /** Prerequisite TC ids in expander order, excluding the leaf. */
    public static List<String> prerequisiteIds(List<ManualTestCase> allCases, String leafId) {
        String leaf = leafId == null ? "" : leafId.trim();
        if (leaf.isEmpty()) {
            return List.of();
        }
        List<ManualTestCase> chain = CallBeforeExpander.expand(allCases, List.of(leaf));
        List<String> ids = new ArrayList<>();
        for (ManualTestCase tc : chain) {
            if (tc == null || tc.tcId() == null) {
                continue;
            }
            String id = tc.tcId().trim();
            if (!id.isEmpty() && !leaf.equals(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    public static List<ProvenStep> stepsFor(TcDraft leaf, List<TcDraft> drafts, List<ManualTestCase> allCases) {
        if (leaf == null) {
            return List.of();
        }
        Map<String, TcDraft> byId = index(drafts);
        List<ProvenStep> setup = new ArrayList<>();
        boolean skipPrereqLogin = leaf.needsLoginBeforeMethod();
        for (String preId : prerequisiteIds(allCases, leaf.tcId())) {
            TcDraft pre = byId.get(preId);
            if (!ReuseEligibility.canReuse(pre)) {
                continue;
            }
            if (!skipPrereqLogin && pre.loginSteps() != null) {
                setup.addAll(pre.loginSteps());
            }
            setup.addAll(pre.provenSteps());
        }
        return List.copyOf(setup);
    }

    public static List<TcDraft> applyHonesty(List<TcDraft> drafts, List<ManualTestCase> allCases) {
        List<TcDraft> incoming = drafts == null ? List.of() : drafts;
        Map<String, TcDraft> byId = index(incoming);
        List<TcDraft> out = new ArrayList<>(incoming.size());
        for (TcDraft draft : incoming) {
            if (draft == null) {
                continue;
            }
            String blocked = blockReason(draft, byId, allCases);
            if (blocked != null && isAdvertisedPass(draft)) {
                out.add(demote(draft, blocked));
            } else {
                out.add(draft);
            }
        }
        return List.copyOf(out);
    }

    private static String blockReason(TcDraft leaf, Map<String, TcDraft> byId, List<ManualTestCase> allCases) {
        for (String preId : prerequisiteIds(allCases, leaf.tcId())) {
            if (!ReuseEligibility.canReuse(byId.get(preId))) {
                return BLOCKED_PREFIX + " " + preId + " did not pass";
            }
        }
        return null;
    }

    private static boolean isAdvertisedPass(TcDraft draft) {
        return draft.status() == TcDraftStatus.PASSED || draft.status() == TcDraftStatus.REUSED;
    }

    private static TcDraft demote(TcDraft d, String reason) {
        return new TcDraft(
                d.tcId(), d.title(), d.stepsText(), d.expectedResult(),
                TcDraftStatus.TODO, d.provenSteps(), d.loginSteps(),
                d.needsLoginBeforeMethod(), d.blockerStepIndex(), d.blockerIntent(),
                reason, d.evidenceDir(), d.retryCountOnBlocker(), d.lastPageUrl(),
                d.healTier(), d.healSkipReason(), d.loginFormUrl(),
                d.jobAuthoringEngine(), d.precisionCallsUsed(), d.precisionFallback(),
                d.precisionFallbackReason());
    }

    private static Map<String, TcDraft> index(List<TcDraft> drafts) {
        Map<String, TcDraft> byId = new LinkedHashMap<>();
        if (drafts == null) {
            return byId;
        }
        for (TcDraft draft : drafts) {
            if (draft != null && draft.tcId() != null && !draft.tcId().isBlank()) {
                byId.put(draft.tcId().trim(), draft);
            }
        }
        return byId;
    }
}
