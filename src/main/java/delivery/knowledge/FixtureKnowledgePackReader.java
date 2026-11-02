package delivery.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** In-memory packs for unit/integration tests only — not a production store. */
public final class FixtureKnowledgePackReader implements KnowledgePackReader {
    private final Map<String, KnowledgePack> byKey = new LinkedHashMap<>();

    public FixtureKnowledgePackReader put(KnowledgePack pack) {
        if (pack != null && pack.packId() != null && !pack.packId().isBlank()) {
            byKey.put(key(pack.projectId(), pack.packId()), pack);
        }
        return this;
    }

    @Override
    public List<KnowledgePack> list(String projectId) {
        String want = projectId == null ? "" : projectId.trim();
        List<KnowledgePack> out = new ArrayList<>();
        for (KnowledgePack p : byKey.values()) {
            if (want.equals(p.projectId())) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public Optional<KnowledgePack> get(String projectId, String packId) {
        if (packId == null || packId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(key(projectId, packId.trim())));
    }

    private static String key(String projectId, String packId) {
        String p = projectId == null ? "" : projectId.trim();
        return p.toLowerCase(Locale.ROOT) + "|" + packId.trim();
    }
}