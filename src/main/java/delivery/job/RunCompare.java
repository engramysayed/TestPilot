package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** First meaningful divergence between two pinned runs of the same case identity. */
public final class RunCompare {
    public record Step(String caseId, String name, String expected, String observed) {
    }

    public record Divergence(int index, Step left, Step right, String field) {
    }

    private RunCompare() {
    }

    public static List<Step> fromDrafts(List<TcDraft> drafts) {
        List<Step> out = new ArrayList<>();
        if (drafts == null) {
            return out;
        }
        for (TcDraft draft : drafts) {
            if (draft == null) {
                continue;
            }
            String observed = draft.status() == null ? "" : draft.status().name();
            if (draft.lastPageUrl() != null && !draft.lastPageUrl().isBlank()) {
                observed = observed + " @ " + draft.lastPageUrl();
            }
            if (draft.failureReason() != null && !draft.failureReason().isBlank()) {
                observed = observed + " — " + draft.failureReason();
            }
            out.add(new Step(draft.tcId(), "case", draft.expectedResult(), observed));
            for (ProvenStep step : draft.provenSteps()) {
                if (step == null) {
                    continue;
                }
                String name = step.action() == null || step.action().isBlank() ? "step" : step.action();
                out.add(new Step(
                        draft.tcId(),
                        name,
                        step.assertionExpected() == null ? "" : step.assertionExpected(),
                        step.validated() ? "validated" : "unvalidated"));
            }
        }
        return out;
    }

    public static Divergence firstMeaningful(List<Step> left, List<Step> right) {
        List<Step> a = left == null ? List.of() : left;
        List<Step> b = right == null ? List.of() : right;
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            Step l = i < a.size() ? a.get(i) : null;
            Step r = i < b.size() ? b.get(i) : null;
            if (l == null || r == null) {
                return new Divergence(i, l, r, "presence");
            }
            if (!Objects.equals(nz(l.caseId()), nz(r.caseId()))) {
                return new Divergence(i, l, r, "caseId");
            }
            if (!Objects.equals(nz(l.expected()), nz(r.expected()))) {
                return new Divergence(i, l, r, "expected");
            }
            if (!Objects.equals(nz(l.observed()), nz(r.observed()))) {
                return new Divergence(i, l, r, "observed");
            }
        }
        return null;
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }
}
