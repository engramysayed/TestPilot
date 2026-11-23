package delivery.mobile.run;

import delivery.authoring.PrecisionJobConfig;
import delivery.heal.CursorHealClient;
import delivery.mobile.runner.precision.PrecisionOutcome;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.ArrayList;
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

    private static MobilePrecisionBindService svc(CursorHealClient cursor) {
        return new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> cursor,
                MobilePrecisionPickLog.inert());
    }

    @Test
    public void choosesCandidateWhenCursorReturnsAValidId() {
        PrecisionOutcome out = svc(new FakeCursor(true, "c-a"))
                .bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.code(), "");
        Assert.assertEquals(out.chosenId(), "c-a");
        Assert.assertEquals(out.usedProvider(), "cursor");
        Assert.assertTrue(out.countsAsCursor());
    }

    @Test
    public void rejectsWhenCursorNamesUnknownId() {
        PrecisionOutcome out = svc(new FakeCursor(true, "c-ghost"))
                .bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.code(), "INVALID_ID");
        Assert.assertEquals(out.chosenId(), "");
    }

    @Test
    public void fallsBackWhenCursorRuntimeNotReady() {
        PrecisionOutcome out = svc(new FakeCursor(false, ""))
                .bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.usedProvider(), "keel");
        Assert.assertEquals(out.fallbackReason(), "UNAVAILABLE");
        Assert.assertFalse(out.countsAsCursor());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsWhenRunIsForAnotherTenant() {
        svc(new FakeCursor(true, "c-a")).bind("mrun_t1", "ten_evil", req("NATIVE"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsEmptyCandidates() {
        svc(new FakeCursor(true, "")).bind("mrun_t1", "ten_a", new MobilePrecisionBindService.BindRequest(
                "tap", List.of(), "NATIVE", ""));
    }

    @Test
    public void swallowsCursorRuntimeFailureAndFallsBack() {
        PrecisionOutcome out = svc(new ThrowingCursor())
                .bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.usedProvider(), "keel");
        Assert.assertEquals(out.fallbackReason(), "UNAVAILABLE");
    }

    @Test
    public void budgetCapsRepeatedCursorCalls() {
        // Config says 5 calls per run. Six tries → sixth is BUDGET_EXHAUSTED and never hits Cursor.
        CountingCursor cc = new CountingCursor("c-a");
        MobilePrecisionBindService s = svc(cc);
        for (int i = 0; i < 5; i++) {
            PrecisionOutcome ok = s.bind("mrun_t1", "ten_a", req("NATIVE"));
            Assert.assertEquals(ok.chosenId(), "c-a", "call " + i);
        }
        PrecisionOutcome out = s.bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.fallbackReason(), "BUDGET_EXHAUSTED");
        Assert.assertEquals(out.usedProvider(), "keel");
        Assert.assertEquals(cc.calls, 5, "cursor is not called once the cap is hit");
    }

    @Test
    public void clearRunResetsBudget() {
        CountingCursor cc = new CountingCursor("c-a");
        MobilePrecisionBindService s = svc(cc);
        for (int i = 0; i < 5; i++) s.bind("mrun_t1", "ten_a", req("NATIVE"));
        s.clearRun("mrun_t1");
        PrecisionOutcome out = s.bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertEquals(out.chosenId(), "c-a");
        Assert.assertEquals(cc.calls, 6);
    }

    @Test
    public void truncatesOversizedCandidateShortlist() {
        // Build 30 candidates; service caps at MAX_CANDIDATES (25) so c25..c29 never reach the sidecar.
        List<MobilePrecisionBindService.Candidate> big = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            big.add(new MobilePrecisionBindService.Candidate("c" + i, "label " + i, "NATIVE", false));
        }
        RecordingCursor rec = new RecordingCursor("c0");
        MobilePrecisionBindService s = svc(rec);
        PrecisionOutcome out = s.bind("mrun_t1", "ten_a",
                new MobilePrecisionBindService.BindRequest("tap", big, "NATIVE", ""));
        Assert.assertEquals(out.chosenId(), "c0");
        Assert.assertTrue(rec.lastTable.contains("c0\t"));
        Assert.assertTrue(rec.lastTable.contains("c24\t"));
        Assert.assertFalse(rec.lastTable.contains("c25\t"),
                "shortlist row 25 must be dropped: " + rec.lastTable);
    }

    @Test
    public void shortlistCarriesMobilePreamble() {
        RecordingCursor rec = new RecordingCursor("c-a");
        svc(rec).bind("mrun_t1", "ten_a", req("NATIVE"));
        Assert.assertTrue(rec.lastTable.startsWith("# Mobile Android native candidates"),
                "mobile preamble missing: " + rec.lastTable);
    }

    @Test
    public void pickLogRecordsEachBind() {
        MobilePrecisionPickLog log = new MobilePrecisionPickLog();
        MobilePrecisionBindService s = new MobilePrecisionBindService(
                id -> Optional.of(run()),
                projectId -> cfgWithCursor(),
                model -> new FakeCursor(true, "c-a"),
                log);
        s.bind("mrun_t1", "ten_a", req("NATIVE"));
        s.bind("mrun_t1", "ten_a", req("NATIVE"));
        List<MobilePrecisionPickLog.Entry> entries = log.recent(10);
        Assert.assertEquals(entries.size(), 2);
        Assert.assertEquals(entries.get(0).runId(), "mrun_t1");
        Assert.assertEquals(entries.get(0).chosenId(), "c-a");
        Assert.assertEquals(entries.get(0).candidateCount(), 2);
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

    private static final class CountingCursor extends CursorHealClient {
        private final String id;
        int calls;
        CountingCursor(String id) {
            super(false, "/bin/false", 1);
            this.id = id;
        }
        @Override public boolean isRuntimeReady() { return true; }
        @Override public String pickCandidateId(String intent, String fail, String table,
                                                String excerpt, Path screenshot) {
            calls++;
            return id;
        }
    }

    private static final class RecordingCursor extends CursorHealClient {
        private final String id;
        String lastTable = "";
        RecordingCursor(String id) {
            super(false, "/bin/false", 1);
            this.id = id;
        }
        @Override public boolean isRuntimeReady() { return true; }
        @Override public String pickCandidateId(String intent, String fail, String table,
                                                String excerpt, Path screenshot) {
            lastTable = table == null ? "" : table;
            return id;
        }
    }
}
