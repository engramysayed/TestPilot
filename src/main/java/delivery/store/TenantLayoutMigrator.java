package delivery.store;

import delivery.identity.PublicationLock;
import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moves known legacy project trees under {@code tenants/{id}/projects/} and quarantines
 * ambiguous domain-shared files. Writes a reversible manifest next to the quarantine.
 */
public final class TenantLayoutMigrator {
    public static final String MANIFEST_NAME = "migration-manifest.json";
    public static final String QUARANTINE_DIR = "_quarantine";

    public record Result(Path manifest, Path quarantine, int migrated, int quarantined) {
    }

    private TenantLayoutMigrator() {
    }

    public static Result migrate(Path storeRoot, Map<String, TenantId> knownProjects) throws Exception {
        if (storeRoot == null) {
            throw new IllegalArgumentException("storeRoot is required");
        }
        Map<String, TenantId> known = knownProjects == null ? Map.of() : knownProjects;
        Path tenantsRoot = storeRoot.resolve("tenants");
        Files.createDirectories(tenantsRoot);
        String stamp = Instant.now().toString().replace(":", "").replace(".", "");
        Path quarantine = tenantsRoot.resolve(QUARANTINE_DIR).resolve(stamp);
        Path lock = tenantsRoot.resolve("migrate.lock");
        return PublicationLock.call(lock, () -> {
            JSONArray moved = new JSONArray();
            JSONArray quarantined = new JSONArray();
            if (Files.isDirectory(storeRoot)) {
                try (var children = Files.list(storeRoot)) {
                    for (Path child : children.toList()) {
                        if (!Files.isDirectory(child)) {
                            continue;
                        }
                        String name = child.getFileName().toString();
                        if (DomainStorePaths.isStoreRootReserved(name) || "tenants".equalsIgnoreCase(name)) {
                            continue;
                        }
                        if (DomainStorePaths.looksLikeProject(child)) {
                            relocateProject(storeRoot, child, name, known, moved, quarantined, quarantine);
                            continue;
                        }
                        relocateDomainFolder(storeRoot, child, known, moved, quarantined, quarantine);
                    }
                }
            }
            Files.createDirectories(quarantine);
            JSONObject manifest = new JSONObject();
            manifest.put("version", 1);
            manifest.put("createdAt", Instant.now().toString());
            manifest.put("moved", moved);
            manifest.put("quarantined", quarantined);
            Path manifestFile = quarantine.resolve(MANIFEST_NAME);
            Files.writeString(manifestFile, manifest.toString(2), StandardCharsets.UTF_8);
            return new Result(manifestFile, quarantine, moved.length(), quarantined.length());
        });
    }

    public static void revert(Path storeRoot, Path manifestFile) throws Exception {
        if (storeRoot == null || manifestFile == null || !Files.isRegularFile(manifestFile)) {
            throw new IllegalArgumentException("manifest is required");
        }
        JSONObject root = new JSONObject(Files.readString(manifestFile, StandardCharsets.UTF_8));
        Path lock = storeRoot.resolve("tenants").resolve("migrate.lock");
        PublicationLock.run(lock, () -> {
            JSONArray moved = root.optJSONArray("moved");
            if (moved != null) {
                for (int i = moved.length() - 1; i >= 0; i--) {
                    JSONObject row = moved.getJSONObject(i);
                    moveBack(storeRoot, row.optString("to"), row.optString("from"));
                }
            }
            JSONArray quarantined = root.optJSONArray("quarantined");
            if (quarantined != null) {
                for (int i = 0; i < quarantined.length(); i++) {
                    JSONObject row = quarantined.getJSONObject(i);
                    moveBack(storeRoot, row.optString("to"), row.optString("from"));
                }
            }
            return null;
        });
    }

    private static void relocateDomainFolder(
            Path storeRoot,
            Path domain,
            Map<String, TenantId> known,
            JSONArray moved,
            JSONArray quarantined,
            Path quarantine
    ) throws Exception {
        if (!Files.isDirectory(domain)) {
            return;
        }
        List<Path> children;
        try (var stream = Files.list(domain)) {
            children = stream.toList();
        }
        for (Path child : children) {
            String name = child.getFileName().toString();
            if (Files.isDirectory(child) && DomainStorePaths.looksLikeProject(child)) {
                relocateProject(storeRoot, child, name, known, moved, quarantined, quarantine);
                continue;
            }
            if (Files.isRegularFile(child) && isAmbiguousSiteFile(name)) {
                quarantinePath(storeRoot, child, "ambiguous-domain-shared", quarantined, quarantine);
            }
        }
    }

    private static void relocateProject(
            Path storeRoot,
            Path source,
            String projectId,
            Map<String, TenantId> known,
            JSONArray moved,
            JSONArray quarantined,
            Path quarantine
    ) throws Exception {
        TenantId tenant = known.get(projectId);
        if (tenant == null) {
            quarantinePath(storeRoot, source, "unknown-project", quarantined, quarantine);
            return;
        }
        Path dest = ScopePaths.projectRoot(storeRoot, tenant, projectId);
        if (Files.exists(dest) && !dest.equals(source)) {
            quarantinePath(storeRoot, source, "destination-exists", quarantined, quarantine);
            return;
        }
        Files.createDirectories(dest.getParent());
        Files.move(source, dest);
        JSONObject row = new JSONObject();
        row.put("kind", "project");
        row.put("from", rel(storeRoot, source));
        row.put("to", rel(storeRoot, dest));
        row.put("tenantId", tenant.value());
        moved.put(row);
    }

    private static void quarantinePath(
            Path storeRoot,
            Path source,
            String reason,
            JSONArray quarantined,
            Path quarantine
    ) throws Exception {
        Path dest = uniqueDest(quarantine, rel(storeRoot, source));
        Files.createDirectories(dest.getParent());
        Files.move(source, dest);
        JSONObject row = new JSONObject();
        row.put("kind", "quarantine");
        row.put("from", rel(storeRoot, source));
        row.put("to", rel(storeRoot, dest));
        row.put("reason", reason);
        quarantined.put(row);
    }

    private static Path uniqueDest(Path quarantine, String relative) {
        String safe = relative.replace("\\", "/");
        Path dest = quarantine.resolve(safe);
        int n = 1;
        while (Files.exists(dest)) {
            dest = quarantine.resolve(safe + "." + n);
            n++;
        }
        return dest;
    }

    private static void moveBack(Path storeRoot, String fromRel, String toRel) throws Exception {
        if (fromRel == null || fromRel.isBlank() || toRel == null || toRel.isBlank()) {
            return;
        }
        Path from = storeRoot.resolve(fromRel.replace("/", storeRoot.getFileSystem().getSeparator()));
        Path to = storeRoot.resolve(toRel.replace("/", storeRoot.getFileSystem().getSeparator()));
        if (!Files.exists(from)) {
            return;
        }
        Files.createDirectories(to.getParent());
        if (Files.exists(to)) {
            return;
        }
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean isAmbiguousSiteFile(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals(PreferredHooksStore.FILE_NAME.toLowerCase(Locale.ROOT))
                || n.equals(DomainLocatorMemory.FILE_NAME.toLowerCase(Locale.ROOT));
    }

    private static String rel(Path storeRoot, Path path) {
        return storeRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }
}
