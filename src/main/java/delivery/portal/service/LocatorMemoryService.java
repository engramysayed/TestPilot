package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.portal.model.ProjectRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read / edit / delete for {@link delivery.store.DomainLocatorMemory} entries from the portal UI.
 * Scope is per-project: the project's baseUrl resolves to a site file that multiple projects on
 * the same host may share, and this service reads/writes that shared file directly. Writes are
 * atomic (tmp + ATOMIC_MOVE) and guarded by {@link delivery.identity.PublicationLock} so they do
 * not race engine runs.
 */
@Service
public class LocatorMemoryService {
    public static final String FILE_NAME = "domain-locator-memory.json";

    private final PortalStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    public LocatorMemoryService(PortalStore store) {
        this.store = store;
    }

    public static final class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    /** Lists entries for this project's site. */
    public Map<String, Object> list(String projectId) throws IOException {
        Optional<Path> fileOpt = resolveSiteFile(projectId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("host", fileOpt.map(p -> p.getParent().getFileName().toString()).orElse(""));
        List<Map<String, Object>> entries = fileOpt
                .filter(Files::isRegularFile)
                .map(this::readEntries)
                .orElse(List.of());
        out.put("entries", entries);
        out.put("nearDupes", groupNearDupes(entries));
        return out;
    }

    public Map<String, Object> update(String projectId, String host, String path,
                                      String intentKey, String strategy, String locator,
                                      String action) throws IOException {
        if (intentKey == null || intentKey.isBlank()) {
            throw new IllegalArgumentException("intentKey required");
        }
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator required");
        }
        Path file = resolveSiteFile(projectId).orElseThrow(
                () -> new NotFoundException("No locator memory file for this project's site"));
        Path lock = file.resolveSibling(FILE_NAME + ".lock");
        try {
            return delivery.identity.PublicationLock.<Map<String, Object>>call(lock, () -> {
                if (!Files.isRegularFile(file)) {
                    throw new NotFoundException("No locator memory file yet");
                }
                List<Map<String, Object>> entries = new ArrayList<>(readEntries(file));
                boolean updated = false;
                for (Map<String, Object> e : entries) {
                    if (matches(e, host, path, intentKey)) {
                        e.put("strategy", strategy == null ? "" : strategy);
                        e.put("locator", locator);
                        if (action != null && !action.isBlank()) e.put("action", action);
                        updated = true;
                        break;
                    }
                }
                if (!updated) throw new NotFoundException("Entry not found");
                writeEntries(file, entries);
                return Map.<String, Object>of("ok", true);
            });
        } catch (NotFoundException | IllegalArgumentException re) {
            throw re;
        } catch (Exception e) {
            throw new IOException("update failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> delete(String projectId, String host, String path, String intentKey) throws IOException {
        if (intentKey == null || intentKey.isBlank()) {
            throw new IllegalArgumentException("intentKey required");
        }
        Path file = resolveSiteFile(projectId).orElseThrow(
                () -> new NotFoundException("No locator memory file for this project's site"));
        Path lock = file.resolveSibling(FILE_NAME + ".lock");
        try {
            return delivery.identity.PublicationLock.<Map<String, Object>>call(lock, () -> {
                if (!Files.isRegularFile(file)) {
                    throw new NotFoundException("No locator memory file yet");
                }
                List<Map<String, Object>> entries = new ArrayList<>(readEntries(file));
                boolean removed = entries.removeIf(e -> matches(e, host, path, intentKey));
                if (!removed) throw new NotFoundException("Entry not found");
                writeEntries(file, entries);
                return Map.<String, Object>of("ok", true);
            });
        } catch (NotFoundException | IllegalArgumentException re) {
            throw re;
        } catch (Exception e) {
            throw new IOException("delete failed: " + e.getMessage(), e);
        }
    }

    // --- internals ---

    private Optional<Path> resolveSiteFile(String projectId) {
        ProjectRecord project = store.getProject(projectId).orElse(null);
        if (project == null || project.getBaseUrl() == null || project.getBaseUrl().isBlank()) {
            return Optional.empty();
        }
        delivery.identity.TenantId tenant;
        try {
            tenant = store.filesystemStoreFor(projectId).tenant();
        } catch (Exception e) {
            return Optional.empty();
        }
        Path file = delivery.store.DomainLocatorMemory.sharedFile(
                store.storeRoot(), tenant, project.getBaseUrl());
        return Optional.ofNullable(file);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readEntries(Path file) {
        try {
            Map<String, Object> doc = mapper.readValue(file.toFile(), Map.class);
            Object items = doc.get("entries");
            if (!(items instanceof List<?> raw)) return List.of();
            List<Map<String, Object>> out = new ArrayList<>(raw.size());
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    out.add(copy);
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void writeEntries(Path file, List<Map<String, Object>> entries) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("entries", entries);
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), "locator-memory-ui-", ".tmp");
        try {
            Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException _unused) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static boolean matches(Map<String, Object> e, String host, String path, String intentKey) {
        return eqStr(e.get("host"), host)
                && eqStr(e.get("path"), path)
                && eqStr(e.get("intentKey"), intentKey);
    }

    private static boolean eqStr(Object actual, String expected) {
        String a = actual == null ? "" : String.valueOf(actual);
        String b = expected == null ? "" : expected;
        return a.equals(b);
    }

    /**
     * Returns groups where the same (host, intentKey) appears on more than one path with the same
     * locator — likely the same element remembered after a URL redesign.
     */
    private List<Map<String, Object>> groupNearDupes(List<Map<String, Object>> entries) {
        Map<String, List<Map<String, Object>>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> e : entries) {
            String key = String.valueOf(e.getOrDefault("host", "")) + "|"
                    + String.valueOf(e.getOrDefault("intentKey", "")) + "|"
                    + String.valueOf(e.getOrDefault("locator", ""));
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> grp : byKey.entrySet()) {
            if (grp.getValue().size() > 1) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("key", grp.getKey());
                g.put("entries", grp.getValue());
                out.add(g);
            }
        }
        return out;
    }
}
