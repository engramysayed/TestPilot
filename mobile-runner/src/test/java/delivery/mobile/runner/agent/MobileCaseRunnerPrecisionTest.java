package delivery.mobile.runner.agent;

import delivery.mobile.runner.action.MobileDriverPort;
import delivery.mobile.runner.action.NormalizedSwipe;
import delivery.mobile.runner.bind.ActionKind;
import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Phase 7b: ambiguity hook. When text-contains yields more than one actionable hit and a
 * {@link MobileCaseRunner.PrecisionBinder} is wired, the runner delegates the pick to it and
 * trusts the returned node index only when it's one of the hits. Any other outcome
 * (null binder, empty pick, out-of-list pick, thrown exception) → {@code Optional.empty()}
 * so the existing not-found path keeps running.
 */
public class MobileCaseRunnerPrecisionTest {

    @Test
    public void ambiguousTwoClickableSignInButtons_precisionPicksOne() {
        MobileSnapshot snap = ambiguousSignIn();
        AtomicReference<String> intentSeen = new AtomicReference<>();
        AtomicReference<Integer> sizeSeen = new AtomicReference<>();
        MobileCaseRunner.PrecisionBinder binder = (intent, candidates) -> {
            intentSeen.set(intent);
            sizeSeen.set(candidates.size());
            return Optional.of(2);
        };
        MobileCaseRunner r = new MobileCaseRunner(new NoopDriver(), "com.example", "", null, binder);
        assertEquals(r.bind(snap, "sign in", ActionKind.TAP), Optional.of(2));
        assertEquals(intentSeen.get(), "sign in");
        assertEquals(sizeSeen.get().intValue(), 2);
    }

    @Test
    public void ambiguousWithoutPrecision_fallsBackToNotFound() {
        MobileSnapshot snap = ambiguousSignIn();
        MobileCaseRunner r = new MobileCaseRunner(new NoopDriver(), "com.example", "", null, null);
        assertTrue(r.bind(snap, "sign in", ActionKind.TAP).isEmpty());
    }

    @Test
    public void precisionReturnsIndexNotInHits_isIgnored() {
        MobileSnapshot snap = ambiguousSignIn();
        MobileCaseRunner r = new MobileCaseRunner(new NoopDriver(), "com.example", "", null,
                (intent, candidates) -> Optional.of(99));
        assertTrue(r.bind(snap, "sign in", ActionKind.TAP).isEmpty());
    }

    @Test
    public void precisionThrows_fallsBack() {
        MobileSnapshot snap = ambiguousSignIn();
        MobileCaseRunner r = new MobileCaseRunner(new NoopDriver(), "com.example", "", null,
                (intent, candidates) -> { throw new RuntimeException("sidecar down"); });
        assertTrue(r.bind(snap, "sign in", ActionKind.TAP).isEmpty());
    }

    @Test
    public void unambiguousOneHit_doesNotCallPrecision() {
        MobileSnapshot snap = singleSignIn();
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        MobileCaseRunner r = new MobileCaseRunner(new NoopDriver(), "com.example", "", null,
                (intent, candidates) -> { called.set(true); return Optional.of(99); });
        assertEquals(r.bind(snap, "sign in", ActionKind.TAP), Optional.of(1));
        assertEquals(called.get(), Boolean.FALSE);
    }

    private static MobileSnapshot ambiguousSignIn() {
        // Two Button nodes with the same label "sign in" that aren't matched by RESOURCE_ID /
        // ACCESSIBILITY_ID / exact TEXT (the ladder picks distinct names), so normalized
        // contains is the only strategy that finds either. clickable=true both.
        MobileSnapshotNode root = node(0, null, "FrameLayout", "", "", "", "", false, List.of(1, 2));
        MobileSnapshotNode a = node(1, 0, "Button", "sign in now", "btn_a", "", "", true, List.of());
        MobileSnapshotNode b = node(2, 0, "Button", "please sign in", "btn_b", "", "", true, List.of());
        return new MobileSnapshot(meta(), List.of(root, a, b), 0, "", 0);
    }

    private static MobileSnapshot singleSignIn() {
        MobileSnapshotNode root = node(0, null, "FrameLayout", "", "", "", "", false, List.of(1));
        MobileSnapshotNode a = node(1, 0, "Button", "sign in please", "btn_a", "", "", true, List.of());
        return new MobileSnapshot(meta(), List.of(root, a), 0, "", 0);
    }

    private static MobileSnapshotNode node(int idx, Integer parent, String cls, String text,
                                           String resourceIdTail, String contentDesc, String hint,
                                           boolean clickable, List<Integer> children) {
        return new MobileSnapshotNode(idx, parent, cls, "com.example", resourceIdTail, contentDesc,
                text, hint, new MobileBounds(0, 0, 100, 100),
                true, true, clickable, false, false, false, false, false, false, false, "0", children);
    }

    private static MobileSnapshotMeta meta() {
        return new MobileSnapshotMeta(1, 1_700_000_000_000L, "com.example", ".Main",
                "NATIVE", 0, 1080, 1920, "en", MobileSnapshotInvalidation.NONE);
    }

    /** Minimal MobileDriverPort stub: bind() never touches the driver. */
    private static final class NoopDriver implements MobileDriverPort {
        @Override public MobileSnapshot pageSource() { return null; }
        @Override public void tap(int nodeIndex) { }
        @Override public void clear(int nodeIndex) { }
        @Override public void typeUnicode(int nodeIndex, String value) { }
        @Override public void typeAscii(int nodeIndex, String value) { }
        @Override public String readText(int nodeIndex) { return ""; }
        @Override public byte[] screenshot() { return new byte[0]; }
        @Override public void swipeNormalized(NormalizedSwipe swipe) { }
    }
}
