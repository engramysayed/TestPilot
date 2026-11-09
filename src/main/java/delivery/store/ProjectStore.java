package delivery.store;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ProjectStore {
    private final Path storeRoot;
    private final String domainHint;
    private final delivery.identity.TenantId tenant;

    public ProjectStore(Path storeRoot) {
        this(storeRoot, null, null);
    }

    public ProjectStore(Path storeRoot, String baseUrlOrHost) {
        this(storeRoot, baseUrlOrHost, null);
    }

    public ProjectStore(Path storeRoot, String baseUrlOrHost, delivery.identity.TenantId tenant) {
        this.storeRoot = storeRoot;
        this.domainHint = baseUrlOrHost;
        this.tenant = tenant;
    }

    public Path storeRoot() {
        return storeRoot;
    }

    public delivery.identity.TenantId tenant() {
        return tenant;
    }

    public Path projectRoot(String projectId) {
        if (tenant != null) {
            return delivery.identity.ScopePaths.projectRoot(storeRoot, tenant, projectId);
        }
        return DomainStorePaths.resolveProjectRoot(storeRoot, domainHint, projectId);
    }

    public boolean hasFramework(String projectId) {
        return Files.isDirectory(projectRoot(projectId).resolve("framework"));
    }

    public StoredProject load(String projectId) throws Exception {
        Path root = projectRoot(projectId);
        Path meta = root.resolve("project.json");
        int version = 0;
        if (Files.exists(meta)) {
            org.json.JSONObject json = new org.json.JSONObject(Files.readString(meta, StandardCharsets.UTF_8));
            version = json.optInt("version", 0);
        }
        return new StoredProject(projectId, root, version);
    }

    public StoredProject saveVersion(String projectId, Path frameworkDir, Path zipFile) throws Exception {
        return saveVersion(projectId, frameworkDir, zipFile, null,
                delivery.job.PublicationGuard.cancellable(() -> false));
    }

    public StoredProject saveVersion(String projectId, Path frameworkDir, Path zipFile,
                                    Path stagedState, delivery.job.PublicationGuard guard) throws Exception {
        Path root = projectRoot(projectId);
        Path lockFile = root.resolve(".publish.lock");
        return delivery.identity.PublicationLock.call(lockFile, () -> guard.publish(() -> {
            Path framework = root.resolve("framework");
            Files.createDirectories(root.resolve("versions"));
            if (Files.exists(framework)) {
                deleteRecursive(framework);
            }
            copyRecursive(frameworkDir, framework);
            scrubLocalSecretProperties(framework);
            StoredProject current = load(projectId);
            int next = current.version() + 1;
            Path versioned = root.resolve("versions").resolve("v" + next + ".zip");
            Files.copy(zipFile, versioned, StandardCopyOption.REPLACE_EXISTING);
            String sha256 = sha256(versioned);
            org.json.JSONObject meta = new org.json.JSONObject();
            meta.put("projectId", projectId);
            meta.put("version", next);
            meta.put("sha256", sha256);
            org.json.JSONArray artifacts = new org.json.JSONArray();
            Path metaFile = root.resolve("project.json");
            if (Files.exists(metaFile)) {
                org.json.JSONObject prior = new org.json.JSONObject(Files.readString(metaFile, StandardCharsets.UTF_8));
                org.json.JSONArray old = prior.optJSONArray("artifacts");
                if (old != null) {
                    for (int i = 0; i < old.length(); i++) {
                        org.json.JSONObject row = old.getJSONObject(i);
                        row.put("expired", true);
                        artifacts.put(row);
                    }
                }
            }
            artifacts.put(new org.json.JSONObject()
                    .put("version", next)
                    .put("sha256", sha256)
                    .put("kind", "CONVERT")
                    .put("expired", false));
            meta.put("artifacts", artifacts);
            if (domainHint != null && !domainHint.isBlank()) {
                meta.put("domain", DomainStorePaths.folderNameFromHostOrUrl(domainHint));
            }
            if (tenant != null) {
                meta.put("tenantId", tenant.value());
            }
            if (stagedState != null && Files.isDirectory(stagedState)) {
                try (var entries = Files.list(stagedState)) {
                    for (Path entry : entries.toList()) {
                        Path dest = root.resolve(entry.getFileName());
                        if (Files.isDirectory(entry)) {
                            if (!entry.getFileName().toString().equals("evidence")) deleteRecursive(dest);
                            copyRecursive(entry, dest);
                        } else Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Files.writeString(root.resolve("project.json"), meta.toString(2), StandardCharsets.UTF_8);
            return new StoredProject(projectId, root, next);
        }));
    }

    public java.util.Optional<Path> latestVersionZip(String projectId) {
        Path versions = projectRoot(projectId).resolve("versions");
        if (!Files.isDirectory(versions)) {
            return java.util.Optional.empty();
        }
        try {
            return Files.list(versions)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("v") && n.endsWith(".zip");
                    })
                    .max((a, b) -> {
                        int va = versionNumber(a.getFileName().toString());
                        int vb = versionNumber(b.getFileName().toString());
                        return Integer.compare(va, vb);
                    });
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static int versionNumber(String name) {
        try {
            return Integer.parseInt(name.replace("v", "").replace(".zip", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void writeLastJob(String projectId, org.json.JSONObject job) throws Exception {
        Path root = projectRoot(projectId);
        Files.createDirectories(root);
        Files.writeString(root.resolve("last-job.json"), job.toString(2), StandardCharsets.UTF_8);
    }

    /** Removes live {@code framework/} only; version ZIPs and IR stay. */
    public void deleteLiveFramework(String projectId) throws Exception {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        Path framework = projectRoot(projectId).resolve("framework");
        deleteRecursive(framework);
    }

    /** Removes framework, versions, IR, locator map — entire project directory. */
    public void deleteProject(String projectId) throws Exception {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        deleteRecursive(projectRoot(projectId));
    }

    /**
     * Local-only secret property filenames that must never persist under ProjectStore
     * {@code framework/} (ZIP already excludes them via FrameworkPackager).
     */
    static final java.util.Set<String> LOCAL_SECRET_PROPERTY_FILES = java.util.Set.of(
            "delivery-secrets.local.properties",
            "delivery-target.local.properties");

    /** Delete known local secret property files anywhere under a framework tree. */
    static void scrubLocalSecretProperties(Path frameworkRoot) throws Exception {
        if (frameworkRoot == null || !Files.exists(frameworkRoot)) {
            return;
        }
        try (var walk = Files.walk(frameworkRoot)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                if (LOCAL_SECRET_PROPERTY_FILES.contains(path.getFileName().toString())) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void copyRecursive(Path src, Path dest) throws Exception {
        Files.walk(src).forEach(path -> {
            try {
                if (Files.isRegularFile(path)
                        && LOCAL_SECRET_PROPERTY_FILES.contains(path.getFileName().toString())) {
                    return; // never copy local secrets into store framework/
                }
                Path rel = dest.resolve(src.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(rel);
                } else {
                    Files.createDirectories(rel.getParent());
                    Files.copy(path, rel, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        // walkFileTree does NOT follow symlinks by default; Files.walk DOES. A malicious
        // symlink inside the tree would otherwise escape when the run is purged.
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFileFailed(Path file, java.io.IOException exc)
                    throws java.io.IOException {
                // broken symlinks land here; still unlink the entry itself
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, java.io.IOException exc)
                    throws java.io.IOException {
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static String sha256(Path file) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }
}
