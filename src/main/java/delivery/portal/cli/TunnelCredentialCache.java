package delivery.portal.cli;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Optional;

/**
 * Local cache of tunnel agent credentials ({@code KEEL_HOME/tunnel/<portal>__<project>.json}) so that
 * {@code keel tunnel start} can ask the portal to reuse its enrollment instead of minting a new token
 * on every run.
 */
final class TunnelCredentialCache {

    private TunnelCredentialCache() {
    }

    static Path file(String portalBase, String projectId) {
        String home = System.getenv("KEEL_HOME");
        Path root = home == null || home.isBlank()
                ? Path.of(System.getProperty("user.home"), ".keel")
                : Path.of(home.trim());
        return root.resolve("tunnel").resolve(safe(portalBase) + "__" + safe(projectId) + ".json");
    }

    static Optional<JSONObject> load(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            JSONObject o = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            if (o.optString("token", "").isBlank() || o.optString("agentSecret", "").isBlank()) {
                return Optional.empty();
            }
            return Optional.of(o);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static void save(Path file, String projectId, String portalBase, String token, String agentSecret) {
        try {
            Files.createDirectories(file.getParent());
            JSONObject o = new JSONObject()
                    .put("projectId", projectId)
                    .put("portalUrl", portalBase)
                    .put("token", token)
                    .put("agentSecret", agentSecret)
                    .put("savedAt", Instant.now().toString());
            Files.writeString(file, o.toString(2), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: file lives under the user profile, which is already per-user.
            }
        } catch (Exception e) {
            System.err.println("warning: could not cache tunnel credentials at " + file + ": " + e.getMessage());
        }
    }

    private static String safe(String s) {
        String v = s == null ? "" : s.trim().toLowerCase();
        v = v.replaceFirst("^https?://", "");
        return v.replaceAll("[^a-z0-9._-]+", "_");
    }
}
