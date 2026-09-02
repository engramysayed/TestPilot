package delivery.store;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ProjectStore {
    private final Path storeRoot;
    private final String domainHint;

    public ProjectStore(Path storeRoot) {
        this(storeRoot, null);
    }

    public ProjectStore(Path storeRoot, String baseUrlOrHost) {
        this.storeRoot = storeRoot;
        this.domainHint = baseUrlOrHost;
    }

    public Path storeRoot() {
        return storeRoot;
    }

    public Path projectRoot(String projectId) {
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
        Path root = projectRoot(projectId);
        Path framework = root.resolve("framework");
        Files.createDirectories(root.resolve("versions"));
        if (Files.exists(framework)) {
            deleteRecursive(framework);
        }
        copyRecursive(frameworkDir, framework);
        StoredProject current = load(projectId);
        int next = current.version() + 1;
        Path versioned = root.resolve("versions").resolve("v" + next + ".zip");
        Files.copy(zipFile, versioned, StandardCopyOption.REPLACE_EXISTING);
        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("projectId", projectId);
        meta.put("version", next);
        if (domainHint != null && !domainHint.isBlank()) {
            meta.put("domain", DomainStorePaths.folderNameFromHostOrUrl(domainHint));
        }
        Files.writeString(root.resolve("project.json"), meta.toString(2), StandardCharsets.UTF_8);
        return new StoredProject(projectId, root, next);
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

    /** Removes framework, versions, IR, locator map — entire project directory. */
    public void deleteProject(String projectId) throws Exception {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        deleteRecursive(projectRoot(projectId));
    }

    private static void copyRecursive(Path src, Path dest) throws Exception {
        Files.walk(src).forEach(path -> {
            try {
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
        Files.walk(root)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
