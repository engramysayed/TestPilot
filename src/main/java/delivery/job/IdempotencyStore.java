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
