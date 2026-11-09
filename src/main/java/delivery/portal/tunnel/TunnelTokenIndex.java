package delivery.portal.tunnel;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class TunnelTokenIndex {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TunnelTokenIndex() {
    }

    public static Optional<String> projectIdForToken(Path storeRoot, String token) {
        if (storeRoot == null || token == null || token.isBlank()) {
            return Optional.empty();
        }
        Path index = storeRoot.resolve("tunnel-index").resolve(token.trim() + ".json");
        if (!Files.isRegularFile(index)) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = MAPPER.readValue(index.toFile(), Map.class);
            String pid = String.valueOf(doc.getOrDefault("projectId", "")).trim();
            return pid.isBlank() ? Optional.empty() : Optional.of(pid);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
