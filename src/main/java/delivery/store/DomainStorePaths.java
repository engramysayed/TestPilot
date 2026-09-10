package delivery.store;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves delivery-store paths as {@code <storeRoot>/<domain>/<projectId>/}.
 * Legacy flat {@code <storeRoot>/<projectId>/} is still found when present.
 * Does not touch portal DB files at the store root.
 */
public final class DomainStorePaths {
    private DomainStorePaths() {
    }

    public static String folderNameFromHostOrUrl(String hostOrUrl) {
        if (hostOrUrl == null || hostOrUrl.isBlank()) {
            return "";
        }
        String host = extractHost(hostOrUrl.trim());
        if (host.isBlank()) {
            return sanitizeFolder(hostOrUrl.trim());
        }
        return sanitizeFolder(host);
    }

    public static String extractHost(String hostOrUrl) {
        String raw = hostOrUrl == null ? "" : hostOrUrl.trim();
        if (raw.isBlank()) {
            return "";
        }
        try {
            String withScheme = raw.contains("://") ? raw : "https://" + raw;
            URI uri = URI.create(withScheme);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.toLowerCase(Locale.ROOT);
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        String noPath = raw.split("[/?#]", 2)[0];
        int colon = noPath.indexOf(':');
        if (colon > 0 && !noPath.contains("://")) {
            // host:port without scheme
            noPath = noPath.substring(0, colon);
        }
        return noPath.toLowerCase(Locale.ROOT).replaceAll("^www\\.", "");
    }

    public static String sanitizeFolder(String hostOrToken) {
        if (hostOrToken == null || hostOrToken.isBlank()) {
            return "";
        }
        String s = hostOrToken.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("^www\\.", "");
        s = s.replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s;
    }

    /** {@code <storeRoot>/<domain>/} — shared site files live here, not under a project id. */
    public static Path resolveDomainRoot(Path storeRoot, String domainFolderOrUrl) {
        if (storeRoot == null) {
            return null;
        }
        String domain = folderNameFromHostOrUrl(domainFolderOrUrl);
        if (domain.isBlank()) {
            return null;
        }
        return storeRoot.resolve(domain);
    }

    /**
     * Prefer nested {@code domain/projectId}; reuse legacy flat project dirs when they already exist.
     */
    public static Path resolveProjectRoot(Path storeRoot, String domainFolderOrUrl, String projectId) {
        if (storeRoot == null) {
            throw new IllegalArgumentException("storeRoot is required");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        String domain = folderNameFromHostOrUrl(domainFolderOrUrl);
        Path flat = storeRoot.resolve(projectId);
        if (!domain.isBlank()) {
            Path nested = storeRoot.resolve(domain).resolve(projectId);
            if (Files.exists(nested)) {
                return nested;
            }
            if (looksLikeProject(flat)) {
                return flat;
            }
            return nested;
        }
        if (Files.exists(flat)) {
            return flat;
        }
        Optional<Path> found = findNestedProject(storeRoot, projectId);
        return found.orElse(flat);
    }

    public static Optional<Path> findNestedProject(Path storeRoot, String projectId) {
        if (storeRoot == null || !Files.isDirectory(storeRoot) || projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        try (Stream<Path> domains = Files.list(storeRoot)) {
            return domains
                    .filter(Files::isDirectory)
                    .filter(p -> !isStoreRootReserved(p.getFileName().toString()))
                    .map(d -> d.resolve(projectId))
                    .filter(DomainStorePaths::looksLikeProject)
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static boolean looksLikeProject(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        return Files.isRegularFile(dir.resolve("project.json"))
                || Files.isDirectory(dir.resolve("framework"))
                || Files.isRegularFile(dir.resolve("locator-map.json"))
                || Files.isRegularFile(dir.resolve("last-job.json"));
    }

    public static boolean isStoreRootReserved(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("portal-db")
                || n.equals("delivery.secret")
                || n.startsWith(".")
                || n.equals("lost+found");
    }

    /**
     * Moves legacy flat project folders that look like domain names into {@code domain/projectId}.
     * Skips reserved names and folders that already have nested projects.
     */
    public static List<String> migrateLegacyFlatProjects(Path storeRoot) throws Exception {
        List<String> moved = new ArrayList<>();
        if (storeRoot == null || !Files.isDirectory(storeRoot)) {
            return moved;
        }
        List<Path> children;
        try (Stream<Path> stream = Files.list(storeRoot)) {
            children = stream.filter(Files::isDirectory).toList();
        }
        for (Path child : children) {
            String name = child.getFileName().toString();
            if (isStoreRootReserved(name)) {
                continue;
            }
            if (!looksLikeProject(child)) {
                continue;
            }
            // Already a domain folder containing projects?
            boolean hasNestedProject;
            try (Stream<Path> nested = Files.list(child)) {
                hasNestedProject = nested
                        .filter(Files::isDirectory)
                        .anyMatch(DomainStorePaths::looksLikeProject);
            }
            if (hasNestedProject) {
                continue;
            }
            Path dest = storeRoot.resolve(name).resolve(name);
            if (Files.exists(dest)) {
                continue;
            }
            // Cannot move a directory into itself — stage beside the store root first.
            Path staging = storeRoot.resolve(name + ".__mig__");
            if (Files.exists(staging)) {
                continue;
            }
            Files.move(child, staging);
            Files.createDirectories(dest.getParent());
            Files.move(staging, dest);
            moved.add(name + " -> " + name + "/" + name);
        }
        return moved;
    }
}
