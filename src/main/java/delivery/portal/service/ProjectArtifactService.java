package delivery.portal.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class ProjectArtifactService {
    public static final int PREVIEW_MAX_BYTES = 64 * 1024;

    public static class BadPathException extends RuntimeException {
        public BadPathException(String message) {
            super(message);
        }
    }

    public static class RunningJobException extends RuntimeException {
        public RunningJobException() {
            super("Cannot modify artifacts while a job is running");
        }
    }

    public Map<String, Object> buildTree(Path projectRoot) throws IOException {
        return buildTree(projectRoot, null);
    }

    /**
     * @param packageZip optional {@code versions/vN.zip} — when set, pages/tests come from that ZIP
     *                   instead of the live {@code framework/} folder.
     */
    public Map<String, Object> buildTree(Path projectRoot, String packageZip) throws IOException {
        Map<String, Object> tree = new HashMap<>();
        tree.put("packages", listPackages(projectRoot));
        String pkg = normalizePackageParam(packageZip);
        tree.put("viewingPackage", pkg);
        if (pkg == null) {
            tree.put("pages", listJavaArtifacts(projectRoot, "pages"));
            Map<String, Object> tests = new HashMap<>();
            tests.put("generated", listJavaArtifacts(projectRoot, "tests/generated"));
            tests.put("todo", listJavaArtifacts(projectRoot, "tests/todo"));
            tree.put("tests", tests);
        } else {
            Map<String, List<Map<String, Object>>> fromZip = listJavaArtifactsFromZip(projectRoot, pkg);
            tree.put("pages", fromZip.getOrDefault("pages", List.of()));
            Map<String, Object> tests = new HashMap<>();
            tests.put("generated", fromZip.getOrDefault("generated", List.of()));
            tests.put("todo", fromZip.getOrDefault("todo", List.of()));
            tree.put("tests", tests);
        }
        return tree;
    }

    public String validateRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BadPathException("Path required");
        }
        String normalized = Path.of(path.replace('\\', '/')).normalize().toString().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new BadPathException("Path not allowed");
        }
        if (!isAllowlisted(normalized)) {
            throw new BadPathException("Path not allowed");
        }
        return normalized;
    }

    public Path resolveAllowedFile(Path projectRoot, String relativePath) throws IOException {
        String normalized = validateRelativePath(relativePath);
        Path resolved = projectRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(projectRoot.normalize())) {
            throw new BadPathException("Path not allowed");
        }
        if (Files.isSymbolicLink(resolved) || Files.isSymbolicLink(resolved.getParent())) {
            throw new BadPathException("Path not allowed");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new BadPathException("File not found");
        }
        return resolved;
    }

    public String preview(Path projectRoot, String relativePath) throws IOException {
        return preview(projectRoot, relativePath, null);
    }

    public String preview(Path projectRoot, String relativePath, String packageZip) throws IOException {
        String pkg = normalizePackageParam(packageZip);
        if (pkg != null) {
            return previewFromZip(projectRoot, pkg, relativePath);
        }
        String normalized = validateRelativePath(relativePath);
        if (!normalized.endsWith(".java")) {
            throw new BadPathException("Preview only supported for Java files");
        }
        Path file = resolveAllowedFile(projectRoot, relativePath);
        long size = Files.size(file);
        if (size > PREVIEW_MAX_BYTES) {
            throw new BadPathException("File too large to preview");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public Path downloadZip(Path projectRoot, String relativePath) throws IOException {
        String normalized = validateRelativePath(relativePath);
        if (!normalized.startsWith("versions/") || !normalized.endsWith(".zip")) {
            throw new BadPathException("Download only supported for version packages");
        }
        return resolveAllowedFile(projectRoot, relativePath);
    }

    public void delete(Path projectRoot, String relativePath) throws IOException {
        Path file = resolveAllowedFile(projectRoot, relativePath);
        Files.delete(file);
    }

    private String normalizePackageParam(String packageZip) {
        if (packageZip == null || packageZip.isBlank()) {
            return null;
        }
        String normalized = Path.of(packageZip.replace('\\', '/')).normalize().toString().replace('\\', '/');
        if (!normalized.matches("versions/v\\d+\\.zip")) {
            throw new BadPathException("Invalid package path");
        }
        return normalized;
    }

    private Map<String, List<Map<String, Object>>> listJavaArtifactsFromZip(Path projectRoot, String packageZip)
            throws IOException {
        Path zipPath = resolveAllowedFile(projectRoot, packageZip);
        List<Map<String, Object>> pages = new ArrayList<>();
        List<Map<String, Object>> generated = new ArrayList<>();
        List<Map<String, Object>> todo = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeZipEntry(entry.getName());
                if (!isZipJavaAllowlisted(name)) {
                    continue;
                }
                String base = Path.of(name).getFileName().toString().replace(".java", "");
                Map<String, Object> item = new HashMap<>();
                item.put("path", name);
                item.put("package", packageZip);
                if (name.contains("/pages/")) {
                    item.put("label", base);
                    pages.add(item);
                } else if (name.contains("/tests/generated/")) {
                    item.put("label", base + "_Generated");
                    generated.add(item);
                } else if (name.contains("/tests/todo/")) {
                    item.put("label", base + "_Todo");
                    todo.add(item);
                }
            }
        }
        pages.sort(Comparator.comparing(m -> String.valueOf(m.get("path"))));
        generated.sort(Comparator.comparing(m -> String.valueOf(m.get("path"))));
        todo.sort(Comparator.comparing(m -> String.valueOf(m.get("path"))));
        Map<String, List<Map<String, Object>>> out = new HashMap<>();
        out.put("pages", pages);
        out.put("generated", generated);
        out.put("todo", todo);
        return out;
    }

    private String previewFromZip(Path projectRoot, String packageZip, String entryPath) throws IOException {
        String entry = normalizeZipEntry(entryPath);
        if (!isZipJavaAllowlisted(entry)) {
            throw new BadPathException("Path not allowed");
        }
        Path zipPath = resolveAllowedFile(projectRoot, packageZip);
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry found = findEntry(zip, entry);
            if (found == null || found.isDirectory()) {
                throw new BadPathException("File not found");
            }
            if (found.getSize() > PREVIEW_MAX_BYTES) {
                throw new BadPathException("File too large to preview");
            }
            try (InputStream in = zip.getInputStream(found)) {
                byte[] bytes = in.readNBytes(PREVIEW_MAX_BYTES + 1);
                if (bytes.length > PREVIEW_MAX_BYTES) {
                    throw new BadPathException("File too large to preview");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
    }

    private static ZipEntry findEntry(ZipFile zip, String normalizedEntry) {
        ZipEntry direct = zip.getEntry(normalizedEntry);
        if (direct != null) {
            return direct;
        }
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (normalizeZipEntry(e.getName()).equals(normalizedEntry)) {
                return e;
            }
        }
        return null;
    }

    static String normalizeZipEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadPathException("Path required");
        }
        String n = raw.replace('\\', '/');
        while (n.startsWith("./")) {
            n = n.substring(2);
        }
        if (n.startsWith("/") || n.contains("..")) {
            throw new BadPathException("Path not allowed");
        }
        return n;
    }

    /** ZIP entries use project-relative paths ({@code src/...}), not {@code framework/src/...}. */
    static boolean isZipJavaAllowlisted(String entry) {
        if (entry == null || !entry.toLowerCase(Locale.ROOT).endsWith(".java")) {
            return false;
        }
        String p = entry.replace('\\', '/');
        if (p.contains("..") || p.startsWith("/")) {
            return false;
        }
        // Live-framework-style paths if ever packed that way
        if (p.startsWith("framework/") && isAllowlisted(p)) {
            return true;
        }
        return (p.contains("/pages/") || p.contains("/tests/generated/") || p.contains("/tests/todo/"))
                && (p.startsWith("src/") || p.contains("/src/"));
    }

    private List<Map<String, Object>> listPackages(Path projectRoot) throws IOException {
        Path versions = projectRoot.resolve("versions");
        if (!Files.isDirectory(versions)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(versions)) {
            stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.matches("v\\d+\\.zip");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            if (Files.isSymbolicLink(p) || !Files.isRegularFile(p)) {
                                return;
                            }
                            String fileName = p.getFileName().toString();
                            Map<String, Object> item = new HashMap<>();
                            item.put("path", "versions/" + fileName);
                            item.put("label", fileName.replace(".zip", ""));
                            item.put("sizeBytes", Files.size(p));
                            out.add(item);
                        } catch (IOException ignored) {
                        }
                    });
        }
        return out;
    }

    private List<Map<String, Object>> listJavaArtifacts(Path projectRoot, String segment) throws IOException {
        Path framework = projectRoot.resolve("framework");
        if (!Files.isDirectory(framework)) {
            return List.of();
        }
        String marker = "/" + segment.replace('\\', '/');
        List<Map<String, Object>> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(framework)) {
            walk.filter(p -> {
                        if (Files.isSymbolicLink(p) || !Files.isRegularFile(p)) {
                            return false;
                        }
                        String name = p.getFileName().toString();
                        if (!name.endsWith(".java")) {
                            return false;
                        }
                        String rel = framework.relativize(p).toString().replace('\\', '/');
                        return rel.contains(marker) && isAllowlisted("framework/" + rel);
                    })
                    .sorted(Comparator.comparing(p -> p.toString()))
                    .forEach(p -> {
                        String rel = "framework/" + framework.relativize(p).toString().replace('\\', '/');
                        String base = p.getFileName().toString().replace(".java", "");
                        String label = artifactLabel(segment, base);
                        Map<String, Object> item = new HashMap<>();
                        item.put("path", rel);
                        item.put("label", label);
                        out.add(item);
                    });
        }
        return out;
    }

    private static String artifactLabel(String segment, String base) {
        if (segment.contains("pages")) {
            return base;
        }
        if (segment.contains("generated")) {
            return base + "_Generated";
        }
        return base + "_Todo";
    }

    static boolean isAllowlisted(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        String p = normalized.replace('\\', '/');
        if (p.startsWith("evidence/") || p.startsWith("ir/") || "project.json".equals(p)) {
            return false;
        }
        if (p.matches("versions/v\\d+\\.zip")) {
            return true;
        }
        if (p.startsWith("framework/") && p.endsWith(".java")) {
            return p.contains("/pages/") || p.contains("/tests/generated/") || p.contains("/tests/todo/");
        }
        return false;
    }
}
