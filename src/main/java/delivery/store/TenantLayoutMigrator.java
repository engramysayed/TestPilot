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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Moves known legacy project trees under {@code tenants/{id}/projects/} and quarantines
 * ambiguous domain-shared files. Writes a reversible manifest next to the quarantine.
 */
public final class TenantLayoutMigrator {
    public static final String MANIFEST_NAME = "migration-manifest.json";
    public static final String JOURNAL_NAME = "migrate-journal.json";
    public static final String QUARANTINE_DIR = "_quarantine";

    public static final class InterruptedMigrationException extends IllegalStateException {
        public InterruptedMigrationException() {
            super("migration interrupted");
        }
    }

    public record Result(Path manifest, Path quarantine, int migrated, int quarantined) {
    }

    private TenantLayoutMigrator() {
    }

    public static Path journalFile(Path storeRoot) {
        return storeRoot.resolve("tenants").resolve(JOURNAL_NAME);
    }

    public static Result migrate(Path storeRoot, Map<String, TenantId> knownProjects) throws Exception {
        return migrate(storeRoot, knownProjects, Integer.MAX_VALUE);
    }

    public static Result migrate(Path storeRoot, Map<String, TenantId> knownProjects, int crashAfterOps)
            throws Exception {
        if (storeRoot == null) {
            throw new IllegalArgumentException("storeRoot is required");
        }
        Map<String, TenantId> known = knownProjects == null ? Map.of() : knownProjects;
        Path tenantsRoot = storeRoot.resolve("tenants");
        Files.createDirectories(tenantsRoot);
        Path lock = tenantsRoot.resolve("migrate.lock");
        return PublicationLock.call(lock, () -> runLocked(storeRoot, known, crashAfterOps));
    }

    public static Result recover(Path storeRoot) throws Exception {
        return recover(storeRoot, Integer.MAX_VALUE);
    }

    public static Result recover(Path storeRoot, int crashAfterOps) throws Exception {
        Path journal = journalFile(storeRoot);
        if (!Files.isRegularFile(journal)) {
            throw new IllegalStateException("no migration journal");
        }
        JSONObject root = new JSONObject(Files.readString(journal, StandardCharsets.UTF_8));
        Map<String, TenantId> known = knownFrom(root.optJSONObject("known"));
        if ("complete".equals(root.optString("status"))) {
            Path quarantine = storeRoot.resolve(root.optString("quarantine"));
            JSONArray moved = root.optJSONArray("moved");
            JSONArray quarantined = root.optJSONArray("quarantined");
            return new Result(
                    quarantine.resolve(MANIFEST_NAME),
                    quarantine,
                    moved == null ? 0 : moved.length(),
                    quarantined == null ? 0 : quarantined.length());
        }
        return migrate(storeRoot, known, crashAfterOps);
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

    private static Result runLocked(Path storeRoot, Map<String, TenantId> known, int crashAfterOps)
            throws Exception {
        Session session = Session.open(storeRoot, known, crashAfterOps);
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
                        relocateProject(session, child, name);
                        continue;
                    }
                    relocateDomainFolder(session, child);
                }
            }
        }
        Files.createDirectories(session.quarantine);
        JSONObject manifest = new JSONObject();
        manifest.put("version", 1);
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("moved", session.moved);
        manifest.put("quarantined", session.quarantined);
        Path manifestFile = session.quarantine.resolve(MANIFEST_NAME);
        Files.writeString(manifestFile, manifest.toString(2), StandardCharsets.UTF_8);
        session.status = "complete";
        session.checkpoint();
        return new Result(manifestFile, session.quarantine, session.moved.length(), session.quarantined.length());
    }

    private static void relocateDomainFolder(Session session, Path domain) throws Exception {
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
                relocateProject(session, child, name);
                continue;
            }
            if (Files.isRegularFile(child) && isAmbiguousSiteFile(name)) {
                quarantinePath(session, child, "ambiguous-domain-shared");
            }
        }
    }

    private static void relocateProject(Session session, Path source, String projectId) throws Exception {
        TenantId tenant = session.known.get(projectId);
        if (tenant == null) {
            quarantinePath(session, source, "unknown-project");
            return;
        }
        Path dest = ScopePaths.projectRoot(session.storeRoot, tenant, projectId);
        if (Files.exists(dest) && !dest.equals(source)) {
            quarantinePath(session, source, "destination-exists");
            return;
        }
        Files.createDirectories(dest.getParent());
        Files.move(source, dest);
        JSONObject row = new JSONObject();
        row.put("kind", "project");
        row.put("from", rel(session.storeRoot, source));
        row.put("to", rel(session.storeRoot, dest));
        row.put("tenantId", tenant.value());
        session.moved.put(row);
        session.bump();
    }

    private static void quarantinePath(Session session, Path source, String reason) throws Exception {
        Path dest = uniqueDest(session.quarantine, rel(session.storeRoot, source));
        Files.createDirectories(dest.getParent());
        Files.move(source, dest);
        JSONObject row = new JSONObject();
        row.put("kind", "quarantine");
        row.put("from", rel(session.storeRoot, source));
        row.put("to", rel(session.storeRoot, dest));
        row.put("reason", reason);
        session.quarantined.put(row);
        session.bump();
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

    private static Map<String, TenantId> knownFrom(JSONObject json) {
        Map<String, TenantId> known = new LinkedHashMap<>();
        if (json == null) {
            return known;
        }
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            known.put(id, TenantId.parse(json.getString(id)));
        }
        return known;
    }

    private static final class Session {
        private final Path storeRoot;
        private final Map<String, TenantId> known;
        private final JSONArray moved;
        private final JSONArray quarantined;
        private final Path quarantine;
        private final int crashAfter;
        private int ops;
        private String status;

        private Session(
                Path storeRoot,
                Map<String, TenantId> known,
                JSONArray moved,
                JSONArray quarantined,
                Path quarantine,
                int crashAfter,
                int ops,
                String status
        ) {
            this.storeRoot = storeRoot;
            this.known = known;
            this.moved = moved;
            this.quarantined = quarantined;
            this.quarantine = quarantine;
            this.crashAfter = crashAfter;
            this.ops = ops;
            this.status = status;
        }

        static Session open(Path storeRoot, Map<String, TenantId> known, int crashAfter) throws Exception {
            Path journal = journalFile(storeRoot);
            if (Files.isRegularFile(journal)) {
                JSONObject root = new JSONObject(Files.readString(journal, StandardCharsets.UTF_8));
                if ("in-progress".equals(root.optString("status"))) {
                    Path quarantine = storeRoot.resolve(root.optString("quarantine"));
                    JSONArray moved = root.optJSONArray("moved");
                    JSONArray quarantined = root.optJSONArray("quarantined");
                    int ops = (moved == null ? 0 : moved.length()) + (quarantined == null ? 0 : quarantined.length());
                    return new Session(
                            storeRoot,
                            known,
                            moved == null ? new JSONArray() : moved,
                            quarantined == null ? new JSONArray() : quarantined,
                            quarantine,
                            crashAfter,
                            ops,
                            "in-progress");
                }
            }
            String stamp = Instant.now().toString().replace(":", "").replace(".", "");
            Path quarantine = storeRoot.resolve("tenants").resolve(QUARANTINE_DIR).resolve(stamp);
            Session session = new Session(
                    storeRoot, known, new JSONArray(), new JSONArray(), quarantine, crashAfter, 0, "in-progress");
            session.checkpoint();
            return session;
        }

        void bump() throws Exception {
            ops++;
            checkpoint();
            if (ops >= crashAfter) {
                throw new InterruptedMigrationException();
            }
        }

        void checkpoint() throws Exception {
            JSONObject knownJson = new JSONObject();
            for (Map.Entry<String, TenantId> e : known.entrySet()) {
                knownJson.put(e.getKey(), e.getValue().value());
            }
            JSONObject root = new JSONObject();
            root.put("status", status);
            root.put("known", knownJson);
            root.put("moved", moved);
            root.put("quarantined", quarantined);
            root.put("quarantine", rel(storeRoot, quarantine));
            Path journal = journalFile(storeRoot);
            Files.createDirectories(journal.getParent());
            Files.writeString(journal, root.toString(2), StandardCharsets.UTF_8);
        }
    }
}
