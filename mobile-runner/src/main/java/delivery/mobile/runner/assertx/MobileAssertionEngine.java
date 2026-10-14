package delivery.mobile.runner.assertx;

import delivery.mobile.runner.bind.MatchNormalizer;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-light assertion / capture / compare semantics (M2-05).
 * Absent vs empty is explicit; exact ID uses token-boundary aware compare when requested.
 */
public final class MobileAssertionEngine {

    public AssertResult assertVisible(MobileSnapshot snap, List<MobileSnapshotNode> matches, boolean expectVisible) {
        boolean any = matches != null && !matches.isEmpty()
                && matches.stream().anyMatch(MobileSnapshotNode::displayed);
        if (expectVisible && !any) return AssertResult.fail("ASSERTION_FAILED", "expected visible");
        if (!expectVisible && any) return AssertResult.fail("ASSERTION_FAILED", "expected not visible");
        return AssertResult.ok();
    }

    public AssertResult assertExactText(MobileSnapshotNode node, String expected) {
        if (node == null) return AssertResult.fail("ASSERTION_FAILED", "no node");
        String observed = node.password() ? "" : node.rawText();
        if (!MatchNormalizer.normalize(observed).equals(MatchNormalizer.normalize(expected))) {
            return AssertResult.fail("ASSERTION_FAILED", "exact text mismatch");
        }
        return AssertResult.ok();
    }

    /** Exact ID / token boundary: KLA-1001 must not match KLA-10010. */
    public AssertResult assertExactIdBoundary(String observed, String expected) {
        if (observed == null) observed = "";
        if (expected == null) expected = "";
        if (observed.equals(expected)) return AssertResult.ok();
        // token boundary: reject prefix digit extension
        if (observed.startsWith(expected) && observed.length() > expected.length()) {
            char next = observed.charAt(expected.length());
            if (Character.isLetterOrDigit(next) || next == '_' || next == '-') {
                return AssertResult.fail("ASSERTION_FAILED", "token boundary: " + observed + " vs " + expected);
            }
        }
        return AssertResult.fail("ASSERTION_FAILED", "id mismatch");
    }

    /** Absence must examine every match (all-match absence). */
    public AssertResult assertAbsent(List<MobileSnapshotNode> matches) {
        if (matches != null && !matches.isEmpty()) {
            return AssertResult.fail("ASSERTION_FAILED", "expected absent but found " + matches.size());
        }
        return AssertResult.ok();
    }

    public AssertResult assertEmptyText(MobileSnapshotNode node) {
        if (node == null) return AssertResult.fail("ASSERTION_FAILED", "missing node");
        String t = node.password() ? "" : node.rawText();
        if (t == null || t.isEmpty()) return AssertResult.ok();
        return AssertResult.fail("ASSERTION_FAILED", "expected empty text, got length=" + t.length());
    }

    public AssertResult captureText(CaptureStore store, String var, List<MobileSnapshotNode> matches) {
        if (matches == null || matches.isEmpty()) {
            return AssertResult.fail("CAPTURE_MISSING", "no match to capture");
        }
        if (matches.size() > 1) {
            return AssertResult.fail("CAPTURE_AMBIGUOUS", "ambiguous capture matches=" + matches.size());
        }
        String text = matches.get(0).password() ? "" : matches.get(0).rawText();
        store.put(var, text == null ? "" : text);
        return AssertResult.ok();
    }

    public AssertResult compareCaptured(CaptureStore store, String var, String observed, String mode) {
        if (!store.has(var)) {
            return AssertResult.fail("COMPARE_MISSING", "capture var absent: " + var);
        }
        String expected = store.get(var).orElse("");
        String m = mode == null ? "EXACT" : mode.toUpperCase();
        if ("TOKEN_BOUNDARY".equals(m) || "EXACT_ID".equals(m)) {
            return assertExactIdBoundary(observed, expected);
        }
        if (!MatchNormalizer.normalize(expected).equals(MatchNormalizer.normalize(observed))) {
            return AssertResult.fail("ASSERTION_FAILED", "compare mismatch");
        }
        return AssertResult.ok();
    }

    /** Explicit absent-vs-empty: null/missing node vs present-with-empty-text. */
    public AssertResult absentVsEmpty(MobileSnapshotNode node, boolean expectAbsent) {
        if (expectAbsent) {
            return node == null ? AssertResult.ok() : AssertResult.fail("ASSERTION_FAILED", "expected absent");
        }
        return assertEmptyText(node);
    }

    public List<MobileSnapshotNode> displayedOnly(List<MobileSnapshotNode> matches) {
        List<MobileSnapshotNode> out = new ArrayList<>();
        if (matches == null) return out;
        for (MobileSnapshotNode n : matches) if (n.displayed()) out.add(n);
        return out;
    }
}
