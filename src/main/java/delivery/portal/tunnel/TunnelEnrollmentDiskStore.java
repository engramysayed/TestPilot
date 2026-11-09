package delivery.portal.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Persists latest tunnel enrollment per project (A5). */
public final class TunnelEnrollmentDiskStore {
    public static final String DIR = "tunnel";
    public static final String FILE = "enrollment.json";
    public static final String INDEX_DIR = "tunnel-index";

    private final ObjectMapper mapper = new ObjectMapper();

    public void save(
            Path projectRoot,
            Path storeRoot,
            String token,
            String projectId,
            String requestedBy,
            String agentSecret,
            String status
    ) {
        try {
            Path file = projectRoot.resolve(DIR).resolve(FILE);
            Files.createDirectories(file.getParent());
            String previousToken = latest(projectRoot)
                    .map(d -> String.valueOf(d.getOrDefault("token", "")).trim())
                    .orElse("");
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("token", token);
            doc.put("projectId", projectId);
            doc.put("requestedBy", requestedBy);
            doc.put("agentSecret", agentSecret == null ? "" : agentSecret);
            doc.put("status", status);
            doc.put("enrolledAt", Instant.now().toString());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), doc);
            if (storeRoot != null && token != null && !token.isBlank()) {
                writeIndex(storeRoot, token, projectId);
                if (!previousToken.isBlank() && !previousToken.equals(token)) {
                    // Old token can never authenticate again (enrollment.json now holds the new one): drop its index.
                    Files.deleteIfExists(indexFile(storeRoot, previousToken));
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Re-creates the token → project index if it is missing (e.g. store migrated or index pruned). */
    public void ensureIndex(Path storeRoot, String token, String projectId) {
        if (storeRoot == null || token == null || token.isBlank()) {
            return;
        }
        try {
            if (!Files.isRegularFile(indexFile(storeRoot, token))) {
                writeIndex(storeRoot, token, projectId);
            }
        } catch (Exception ignored) {
        }
    }

    private void writeIndex(Path storeRoot, String token, String projectId) throws Exception {
        Path index = indexFile(storeRoot, token);
        Files.createDirectories(index.getParent());
        Map<String, Object> ix = new LinkedHashMap<>();
        ix.put("token", token);
        ix.put("projectId", projectId);
        ix.put("enrolledAt", Instant.now().toString());
        mapper.writerWithDefaultPrettyPrinter().writeValue(index.toFile(), ix);
    }

    private static Path indexFile(Path storeRoot, String token) {
        return storeRoot.resolve(INDEX_DIR).resolve(token.trim() + ".json");
    }

    public void markActive(Path projectRoot, String listenUrl) {
        Optional<Map<String, Object>> latest = latest(projectRoot);
        if (latest.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> doc = new LinkedHashMap<>(latest.get());
            doc.put("status", "active");
            doc.put("listenUrl", listenUrl == null ? "" : listenUrl.trim());
            doc.put("lastHeartbeatAt", Instant.now().toString());
            Path file = projectRoot.resolve(DIR).resolve(FILE);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), doc);
        } catch (Exception ignored) {
        }
    }

    public Optional<Map<String, Object>> latest(Path projectRoot) {
        Path file = projectRoot.resolve(DIR).resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = mapper.readValue(file.toFile(), Map.class);
            return Optional.of(raw);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * {@code active} on disk only means a heartbeat arrived at some point. The tunnel is live only while
     * heartbeats keep arriving within {@code ttl}; otherwise the agent has gone away and jobs must not be
     * routed through the relay.
     */
    public static boolean isLive(Map<String, Object> doc, Duration ttl) {
        if (doc == null || !"active".equalsIgnoreCase(String.valueOf(doc.getOrDefault("status", "")))) {
            return false;
        }
        Object hb = doc.get("lastHeartbeatAt");
        if (hb == null || String.valueOf(hb).isBlank()) {
            return false;
        }
        try {
            Instant last = Instant.parse(String.valueOf(hb).trim());
            return !last.plus(ttl == null ? Duration.ofSeconds(60) : ttl).isBefore(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    /** {@code active}, {@code stale} (active but heartbeat expired), or the stored status. */
    public static String effectiveStatus(Map<String, Object> doc, Duration ttl) {
        String st = String.valueOf(doc.getOrDefault("status", "pending"));
        if ("active".equalsIgnoreCase(st) && !isLive(doc, ttl)) {
            return "stale";
        }
        return st;
    }
}
