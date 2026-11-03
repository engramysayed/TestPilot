package delivery.portal.service;

import delivery.prompts.PromptCatalog;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Owner admin inventory / read / save / diff for classpath + override prompts.
 * Does not change {@link PromptCatalog#get(String)} fail-closed runtime rules.
 */
@Service
public class AdminPromptsService {
    public static final String SOURCE_CLASSPATH = "classpath";
    public static final String SOURCE_OVERRIDE = "override";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public Map<String, Object> listPrompts() throws IOException {
        TreeSet<String> ids = new TreeSet<>();
        ids.addAll(classpathIds());
        ids.addAll(overrideOnlyIds());
        List<Map<String, Object>> items = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("source", PromptCatalog.hasOverrideFile(id) ? SOURCE_OVERRIDE : SOURCE_CLASSPATH);
            items.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("promptsVersion", PromptCatalog.version());
        out.put("overrideDirConfigured", PromptCatalog.overrideDir().isPresent());
        return out;
    }

    public Map<String, Object> getPrompt(String rawId) {
        String id = PromptCatalog.normalizeId(rawId);
        Optional<String> override = PromptCatalog.tryOverrideText(id);
        Optional<String> classpath = PromptCatalog.tryClasspathText(id);
        if (override.isEmpty() && classpath.isEmpty()) {
            throw new NotFoundException("Unknown prompt id: " + id);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("source", override.isPresent() ? SOURCE_OVERRIDE : SOURCE_CLASSPATH);
        out.put("text", override.orElseGet(classpath::orElseThrow));
        classpath.ifPresent(s -> out.put("classpathText", s));
        if (classpath.isEmpty()) {
            out.put("classpathText", null);
        }
        return out;
    }

    public Map<String, Object> savePrompt(String rawId, String text) throws IOException {
        String id = PromptCatalog.normalizeId(rawId);
        Path dir = PromptCatalog.overrideDir().orElse(null);
        if (dir == null) {
            throw new UnsetDirException(
                    "Prompt override directory is not configured. Set absolute property keel.prompts.dir "
                            + "(application.properties) or environment variable KEEL_PROMPTS_DIR, then restart.");
        }
        if (text == null) {
            throw new BadRequestException("body.text is required");
        }
        String ext = ".md";
        for (String candidate : new String[] {".md", ".txt"}) {
            Path existing = dir.resolve(id + candidate).normalize();
            if (existing.startsWith(dir.normalize()) && Files.isRegularFile(existing)) {
                ext = candidate;
                break;
            }
        }
        Path target = dir.resolve(id + ext).normalize();
        if (!target.startsWith(dir.normalize())) {
            throw new BadRequestException("illegal prompt id path");
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, text, StandardCharsets.UTF_8);
        PromptCatalog.clearCache();
        return getPrompt(id);
    }

    public Map<String, Object> diffPrompt(String rawId) {
        String id = PromptCatalog.normalizeId(rawId);
        Optional<String> override = PromptCatalog.tryOverrideText(id);
        Optional<String> classpath = PromptCatalog.tryClasspathText(id);
        if (override.isEmpty() && classpath.isEmpty()) {
            throw new NotFoundException("Unknown prompt id: " + id);
        }
        String left = classpath.orElse("");
        String right = override.orElse(classpath.orElse(""));
        boolean identical = left.equals(right);
        List<Map<String, Object>> lines = lineDiff(left, right);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("source", override.isPresent() ? SOURCE_OVERRIDE : SOURCE_CLASSPATH);
        out.put("identical", identical);
        out.put("unified", toUnified(id, left, right, lines));
        out.put("lines", lines);
        return out;
    }

    public TreeSet<String> classpathIds() throws IOException {
        TreeSet<String> ids = new TreeSet<>();
        Resource[] resources = resolver.getResources("classpath*:prompts/**/*.*");
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            String lower = filename.toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".md") || lower.endsWith(".txt"))) {
                continue;
            }
            String url = resource.getURL().toExternalForm().replace('\\', '/');
            int idx = url.lastIndexOf("/prompts/");
            if (idx < 0) {
                continue;
            }
            String rel = url.substring(idx + "/prompts/".length());
            if (rel.isBlank() || rel.contains("..")) {
                continue;
            }
            if (rel.toLowerCase(Locale.ROOT).endsWith(".md") || rel.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                rel = rel.substring(0, rel.lastIndexOf('.'));
            }
            if (!rel.isBlank() && !PromptCatalog.VERSION_FILE.equals(rel)) {
                ids.add(rel);
            }
        }
        return ids;
    }

    TreeSet<String> overrideOnlyIds() throws IOException {
        TreeSet<String> ids = new TreeSet<>();
        Optional<Path> override = PromptCatalog.overrideDir();
        if (override.isEmpty() || !Files.isDirectory(override.get())) {
            return ids;
        }
        Path root = override.get().normalize();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(name.endsWith(".md") || name.endsWith(".txt"))) {
                    return;
                }
                Path rel = root.relativize(path.normalize());
                String id = rel.toString().replace('\\', '/');
                if (id.endsWith(".md") || id.endsWith(".txt")) {
                    id = id.substring(0, id.lastIndexOf('.'));
                }
                if (!id.isBlank() && !id.contains("..") && !PromptCatalog.VERSION_FILE.equals(id)) {
                    ids.add(id);
                }
            });
        }
        return ids;
    }

    static List<Map<String, Object>> lineDiff(String left, String right) {
        String[] a = left.split("\\R", -1);
        String[] b = right.split("\\R", -1);
        int n = a.length;
        int m = b.length;
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a[i].equals(b[j])) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                out.add(line("eq", a[i], b[j]));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                out.add(line("del", a[i], null));
                i++;
            } else {
                out.add(line("add", null, b[j]));
                j++;
            }
        }
        while (i < n) {
            out.add(line("del", a[i++], null));
        }
        while (j < m) {
            out.add(line("add", null, b[j++]));
        }
        return out;
    }

    private static Map<String, Object> line(String op, String classpath, String override) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("op", op);
        row.put("classpath", classpath);
        row.put("override", override);
        return row;
    }

    static String toUnified(String id, String left, String right, List<Map<String, Object>> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- classpath:").append(id).append('\n');
        sb.append("+++ override:").append(id).append('\n');
        for (Map<String, Object> row : lines) {
            String op = String.valueOf(row.get("op"));
            if ("eq".equals(op)) {
                sb.append(' ').append(row.get("classpath")).append('\n');
            } else if ("del".equals(op)) {
                sb.append('-').append(row.get("classpath")).append('\n');
            } else if ("add".equals(op)) {
                sb.append('+').append(row.get("override")).append('\n');
            }
        }
        return sb.toString();
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class UnsetDirException extends RuntimeException {
        public UnsetDirException(String message) {
            super(message);
        }
    }
}
