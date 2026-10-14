package delivery.job;

import delivery.ir.TcIdentity;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sidecar per-TC retry records under {@code work/retry/} (NOT {@code work/ir/}: TcDraftStore.readAll
 * parses every *.json in ir/ as a TcDraft). Absence of a record = legacy run.
 */
public final class RetryRecordStore {
    private final Path dir;

    public RetryRecordStore(Path workDir) {
        this.dir = workDir.resolve("retry");
    }

    public Path dir() {
        return dir;
    }

    public void write(String tcId, RetryVerdict verdict, int testRetries, int testFailures, int infraRetries,
                      String failureReason) throws Exception {
        Files.createDirectories(dir);
        JSONObject o = new JSONObject();
        o.put("tcId", tcId);
        o.put("verdict", verdict.name());
        o.put("testRetries", testRetries);
        o.put("testFailuresBeforePass", testFailures);
        o.put("infraRetries", infraRetries);
        o.put("failureCategory", failureReason == null || failureReason.isBlank()
                ? JSONObject.NULL : RetryCategory.classify(failureReason).name());
        Files.writeString(dir.resolve(TcIdentity.storageKey(tcId) + ".json"), o.toString(2), StandardCharsets.UTF_8);
    }

    /**
     * Sidecar for a reused TC: verdict REUSED, with the prior run's verdict carried so a reused FLAKY
     * (or HEALED) case stays visibly flagged. Returns true when the prior verdict was FLAKY.
     */
    public boolean writeReused(String tcId, RetryVerdict priorVerdict) throws Exception {
        Files.createDirectories(dir);
        RetryVerdict prior = priorVerdict == null ? RetryVerdict.LEGACY_PASSED : priorVerdict;
        JSONObject o = new JSONObject();
        o.put("tcId", tcId);
        o.put("verdict", RetryVerdict.REUSED.name());
        o.put("reusedFromVerdict", prior.name());
        o.put("reusedFlaky", prior == RetryVerdict.FLAKY);
        o.put("testRetries", 0).put("testFailuresBeforePass", 0).put("infraRetries", 0);
        o.put("failureCategory", JSONObject.NULL);
        Files.writeString(dir.resolve(TcIdentity.storageKey(tcId) + ".json"), o.toString(2), StandardCharsets.UTF_8);
        return prior == RetryVerdict.FLAKY;
    }

    public void writeRun(RunHealth health) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("_run.json"), health.toJson().toString(2), StandardCharsets.UTF_8);
    }

    /** Verdict for a TC, or LEGACY_PASSED/FAILED derived from IR status when no sidecar exists. */
    public RetryVerdict readOrLegacy(String tcId, delivery.ir.TcDraftStatus status, String failureReason) {
        try {
            Path f = dir.resolve(TcIdentity.storageKey(tcId) + ".json");
            if (Files.isRegularFile(f)) {
                return RetryVerdict.valueOf(new JSONObject(Files.readString(f, StandardCharsets.UTF_8)).getString("verdict"));
            }
        } catch (Exception ignored) {
            // fall through to legacy
        }
        if (status == delivery.ir.TcDraftStatus.PASSED) {
            return RetryVerdict.LEGACY_PASSED;
        }
        return RetryVerdict.of(status, 0, failureReason);
    }

    /** Verdict of a TC in a previous run root (for reuse flagging); LEGACY_PASSED when no sidecar. */
    public static RetryVerdict priorVerdict(Path previousRoot, String tcId) {
        if (previousRoot == null) {
            return RetryVerdict.LEGACY_PASSED;
        }
        RetryVerdict v = new RetryRecordStore(previousRoot)
                .readOrLegacy(tcId, delivery.ir.TcDraftStatus.PASSED, "");
        if (v == RetryVerdict.REUSED) {
            // Chain of reuses: follow the carried verdict.
            try {
                Path f = previousRoot.resolve("retry").resolve(TcIdentity.storageKey(tcId) + ".json");
                return RetryVerdict.valueOf(new JSONObject(Files.readString(f, StandardCharsets.UTF_8))
                        .optString("reusedFromVerdict", "LEGACY_PASSED"));
            } catch (Exception e) {
                return RetryVerdict.LEGACY_PASSED;
            }
        }
        return v;
    }
}
