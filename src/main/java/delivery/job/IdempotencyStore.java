package delivery.job;

import delivery.identity.PublicationLock;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tenant-scoped idempotency map for public job submission. */
public final class IdempotencyStore {
    public record Entry(String key, String jobId, String bodyHash) {
    }

    public record Submission(String jobId, boolean replay) {}

    @FunctionalInterface public interface Admit { void run(String jobId) throws Exception; }

    /** Pending reservations are recoverable after a crash either side of the durable job commit. */
    public Submission submit(String key, String bodyHash, java.util.function.Predicate<String> jobExists,
                             Admit admit) throws Exception {
        return PublicationLock.call(file.resolveSibling("idempotency.lock"), () -> {
            JSONObject root = read();
            JSONObject row = root.optJSONObject(key);
            if (row != null && !bodyHash.equals(row.optString("bodyHash")))
                throw new Conflict("idempotency key reused with a different body");
            if (row != null && jobExists.test(row.getString("jobId"))) {
                row.put("state", "ADMITTED");
                write(root);
                return new Submission(row.getString("jobId"), true);
            }
            String jobId = row == null ? "job_ci_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                    : row.getString("jobId");
            root.put(key, new JSONObject().put("jobId", jobId).put("bodyHash", bodyHash).put("state", "PENDING"));
            write(root);
            try {
                admit.run(jobId);
            } catch (Exception failure) {
                // A commit may have completed before a later error. Keep that job replayable.
                if (!jobExists.test(jobId)) { root.remove(key); write(root); }
                throw failure;
            }
            root.getJSONObject(key).put("state", "ADMITTED");
            write(root);
            return new Submission(jobId, false);
        });
    }

    private void write(JSONObject root) throws Exception {
        Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), "idempotency-", ".tmp");
        try {
            Files.writeString(tmp, root.toString(2), StandardCharsets.UTF_8);
            try { Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }

    private final Path file;

    public IdempotencyStore(Path file) {
        this.file = file;
    }

    public Entry putOrGet(String key, String bodyHash, String jobId) throws Exception {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotency key is required");
        }
        String hash = bodyHash == null ? "" : bodyHash;
        return PublicationLock.call(file.resolveSibling("idempotency.lock"), () -> {
            JSONObject root = read();
            if (root.has(key)) {
                JSONObject existing = root.getJSONObject(key);
                String existingHash = existing.optString("bodyHash");
                if (!hash.equals(existingHash)) {
                    throw new Conflict("idempotency key reused with a different body");
                }
                return new Entry(key, existing.optString("jobId"), existingHash);
            }
            if (jobId == null || jobId.isBlank()) {
                throw new IllegalArgumentException("jobId is required for a new idempotency key");
            }
            JSONObject row = new JSONObject();
            row.put("jobId", jobId);
            row.put("bodyHash", hash);
            root.put(key, row);
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
            return new Entry(key, jobId, hash);
        });
    }

    private JSONObject read() throws Exception {
        if (!Files.isRegularFile(file)) {
            return new JSONObject();
        }
        return new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
    }
}
