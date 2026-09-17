package delivery.job;

import delivery.identity.PublicationLock;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Persists user failure classifications without rewriting the original suggestion. */
public final class FailureTriageStore {
    private final Path file;

    public FailureTriageStore(Path file) {
        this.file = file;
    }

    public FailureClassifier.Classification put(
            String jobId,
            FailureClassifier.Kind suggested,
            FailureClassifier.Kind effective
    ) throws Exception {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        if (suggested == null || effective == null) {
            throw new IllegalArgumentException("classification kinds are required");
        }
        FailureClassifier.Classification value = new FailureClassifier.Classification(
                suggested, effective, suggested != effective);
        PublicationLock.call(file.resolveSibling("triage.lock"), () -> {
            JSONObject root = read();
            JSONObject jobs = root.optJSONObject("jobs");
            if (jobs == null) {
                jobs = new JSONObject();
                root.put("jobs", jobs);
            }
            JSONObject row = new JSONObject();
            row.put("suggested", value.suggested().name());
            row.put("effective", value.effective().name());
            row.put("userCorrected", value.userCorrected());
            jobs.put(jobId, row);
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
            return null;
        });
        return value;
    }

    public Optional<FailureClassifier.Classification> get(String jobId) throws Exception {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        JSONObject root = read();
        JSONObject jobs = root.optJSONObject("jobs");
        if (jobs == null || !jobs.has(jobId)) {
            return Optional.empty();
        }
        JSONObject row = jobs.getJSONObject(jobId);
        return Optional.of(new FailureClassifier.Classification(
                FailureClassifier.Kind.valueOf(row.optString("suggested")),
                FailureClassifier.Kind.valueOf(row.optString("effective")),
                row.optBoolean("userCorrected")));
    }

    private JSONObject read() throws Exception {
        if (!Files.isRegularFile(file)) {
            JSONObject empty = new JSONObject();
            empty.put("jobs", new JSONObject());
            return empty;
        }
        return new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
    }
}
