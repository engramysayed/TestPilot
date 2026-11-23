package delivery.mobile.run;

import delivery.authoring.PrecisionJobConfig;
import delivery.heal.CursorHealClient;
import delivery.mobile.runner.precision.PrecisionOutcome;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Unit coverage for the portal-side mobile Precision caller. Stubs the Cursor client so the
 * test runs without a Node sidecar or API key.
 */
public class MobilePrecisionBindServiceTest {

    private static MobileRun run() {
        return new MobileRun("mrun_t1", "ten_a", "proj_x", 1L, "dev_a",
                List.of(new MobileRun.CaseInput("TC_1", "t", List.of(), List.of(), List.of())),
                "com.example", ".MainActivity");
    }

    private static PrecisionJobConfig cfgWithCursor() {
        return new PrecisionJobConfig(true, 5, false, "medium", "auto");
    }

    private static MobilePrecisionBindService.BindRequest req(String stepContext) {
        return new MobilePrecisionBindService.BindRequest(
                "tap sign in",
                List.of(
                        new MobilePrecisionBindService.Candidate("c-a", "Sign in button", stepContext, false),
                        new MobilePrecisionBindService.Candidate("c-b", "Sign in text",  stepContext, false)),
                stepContext, "<xml/>");
    }

    @Test
    public void choosesCandidateWhenCursorReturnsAValidId() {
        MobilePrecisionBindService svc = new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> new FakeCursor(true, "c-a"));
        PrecisionOutcome out = svc.bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.code(), "");
        Assert.assertEquals(out.chosenId(), "c-a");
        Assert.assertEquals(out.usedProvider(), "cursor");
        Assert.assertTrue(out.countsAsCursor());
    }

    @Test
    public void rejectsWhenCursorNamesUnknownId() {
        MobilePrecisionBindService svc = new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> new FakeCursor(true, "c-ghost"));
        PrecisionOutcome out = svc.bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.code(), "INVALID_ID");
        Assert.assertEquals(out.chosenId(), "");
    }

    @Test
    public void fallsBackWhenCursorRuntimeNotReady() {
        MobilePrecisionBindService svc = new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> new FakeCursor(false, ""));
        PrecisionOutcome out = svc.bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.usedProvider(), "keel");
        Assert.assertEquals(out.fallbackReason(), "UNAVAILABLE");
        Assert.assertFalse(out.countsAsCursor());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsWhenRunIsForAnotherTenant() {
        MobilePrecisionBindService svc = new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> new FakeCursor(true, "c-a"));
        svc.bind("mrun_t1", "ten_evil", req("NATIVE"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsEmptyCandidates() {
        MobilePrecisionBindService svc = new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> new FakeCursor(true, ""));
        svc.bind("mrun_t1", "ten_a", new MobilePrecisionBindService.BindRequest(
                "tap", List.of(), "NATIVE", ""));
    }

    @Test
    public void swallowsCursorRuntimeFailureAndFallsBack() {
        MobilePrecisionBindService svc = new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> new ThrowingCursor());
        PrecisionOutcome out = svc.bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.usedProvider(), "keel");
        Assert.assertEquals(out.fallbackReason(), "UNAVAILABLE");
    }

    /** Stub that reports a fixed runtime-ready flag and returns a fixed candidate id. */
    private static final class FakeCursor extends CursorHealClient {
        private final boolean ready;
        private final String id;
        FakeCursor(boolean ready, String id) {
            super(false, "/bin/false", 1);   // enabled=false; we override isRuntimeReady anyway
            this.ready = ready;
            this.id = id == null ? "" : id;
        }
        @Override public boolean isRuntimeReady() { return ready; }
        @Override public String pickCandidateId(String intent, String fail, String table,
                                                String excerpt, Path screenshot) {
            return id;
        }
    }

    private static final class ThrowingCursor extends CursorHealClient {
        ThrowingCursor() { super(false, "/bin/false", 1); }
        @Override public boolean isRuntimeReady() { return true; }
        @Override public String pickCandidateId(String intent, String fail, String table,
                                                String excerpt, Path screenshot) {
            throw new RuntimeException("sidecar exploded");
        }
    }
}
