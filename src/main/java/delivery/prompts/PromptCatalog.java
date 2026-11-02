package delivery.prompts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared prompt loader (AE owns core; Codegen bootstrap expanded in place).
 *
 * <p>Resolve order for id {@code heal/pick}:
 * <ol>
 *   <li>{@link #setOverrideDir(Path)} when set (Spring injection), else system property
 *       {@code keel.prompts.dir}, else env {@code KEEL_PROMPTS_DIR} — file
 *       {@code <dir>/<id>.md} (or {@code .txt}). Override dir must be absolute.</li>
 *   <li>Classpath resource {@code prompts/<id>.md} then {@code .txt}</li>
 * </ol>
 *
 * <p>Classpath hits are cached. When an override dir is set, files are re-read each call
 * (hot-reload). Missing override file fails closed (does not silently fall back).
 *
 * <p>Admin / inventory helpers ({@link #version()}, {@link #tryClasspathText(String)},
 * {@link #tryOverrideText(String)}) do not change runtime {@link #get(String)} fail-closed rules.
 */
public final class PromptCatalog {
    public static final String PROP_DIR = "keel.prompts.dir";
    public static final String ENV_DIR = "KEEL_PROMPTS_DIR";
    public static final String VERSION_FILE = "VERSION";

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    /** Programmatic override (Spring); when non-null, wins over property/env. */
    private static volatile Path programmaticOverride;

    private PromptCatalog() {
    }

    /** Load prompt text by id (relative path without extension), e.g. {@code revise/final-revise}. */
    public static String get(String id) {
        String normalized = normalizeId(id);
        Optional<Path> override = overrideDir();
        if (override.isPresent()) {
            return readOverride(override.get(), normalized);
        }
        return CACHE.computeIfAbsent(normalized, PromptCatalog::readClasspath);
    }

    /**
     * {@link #get(String)} then replace {@code {{name}}} placeholders.
     * Missing keys become empty strings. No nested templates.
     */
    public static String format(String id, Map<String, String> vars) {
        String template = get(id);
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + 64);
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf("{{", i);
            if (open < 0) {
                sb.append(template, i, template.length());
                break;
            }
            sb.append(template, i, open);
            int close = template.indexOf("}}", open + 2);
            if (close < 0) {
                sb.append(template, open, template.length());
                break;
            }
            String name = template.substring(open + 2, close).trim();
            String val = vars.get(name);
            sb.append(val == null ? "" : val);
            i = close + 2;
        }
        return sb.toString();
    }

    /**
     * Prompt pack version: override {@code VERSION} file first, then classpath
     * {@code prompts/VERSION}. Returns {@code unknown} when neither exists.
     */
    public static String version() {
        Optional<Path> override = overrideDir();
        if (override.isPresent()) {
            Path file = override.get().resolve(VERSION_FILE);
            if (Files.isRegularFile(file)) {
                try {
                    String body = Files.readString(file, StandardCharsets.UTF_8).trim();
                    if (!body.isEmpty()) {
                        return body.lines().findFirst().orElse(body).trim();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("failed reading override " + VERSION_FILE, e);
                }
            }
        }
        ClassLoader cl = PromptCatalog.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream("prompts/" + VERSION_FILE)) {
            if (in != null) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!body.isEmpty()) {
                    return body.lines().findFirst().orElse(body).trim();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed reading classpath prompts/" + VERSION_FILE, e);
        }
        return "unknown";
    }

    /** Classpath prompt body when present (ignores override dir). */
    public static Optional<String> tryClasspathText(String id) {
        String normalized = normalizeId(id);
        try {
            return Optional.of(readClasspath(normalized));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * Override file body when override dir is set and {@code <id>.md|.txt} exists;
     * empty when dir unset or file missing (does not throw).
     */
    public static Optional<String> tryOverrideText(String id) {
        String normalized = normalizeId(id);
        Optional<Path> override = overrideDir();
        if (override.isEmpty()) {
            return Optional.empty();
        }
        Path dir = override.get();
        for (String ext : new String[] {".md", ".txt"}) {
            Path file = dir.resolve(normalized + ext).normalize();
            if (!file.startsWith(dir.normalize())) {
                throw new IllegalArgumentException("prompt path escapes override dir: " + id);
            }
            if (Files.isRegularFile(file)) {
                try {
                    return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new IllegalStateException("failed reading override prompt " + file, e);
                }
            }
        }
        return Optional.empty();
    }

    /** True when override dir is set and a {@code .md} or {@code .txt} file exists for id. */
    public static boolean hasOverrideFile(String id) {
        return tryOverrideText(id).isPresent();
    }

    /**
     * Spring / tests: bind {@code keel.prompts.dir} without relying on system properties.
     * Pass {@code null} to clear. Non-null paths must be absolute; directory need not exist yet
     * (missing file still fails on {@link #get}). Clears classpath cache.
     */
    public static void setOverrideDir(Path dir) {
        if (dir == null) {
            programmaticOverride = null;
            CACHE.clear();
            return;
        }
        if (!dir.isAbsolute()) {
            throw new IllegalArgumentException(PROP_DIR + " override must be absolute, got: " + dir);
        }
        programmaticOverride = dir.normalize();
        CACHE.clear();
    }

    /**
     * Absolute override folder when set via {@link #setOverrideDir(Path)}, system property, or env;
     * empty otherwise.
     */
    public static Optional<Path> overrideDir() {
        Path prog = programmaticOverride;
        if (prog != null) {
            return Optional.of(prog);
        }
        String raw = System.getProperty(PROP_DIR);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(ENV_DIR);
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Path p = Path.of(raw.trim());
        if (!p.isAbsolute()) {
            throw new IllegalStateException(
                    PROP_DIR + " / " + ENV_DIR + " must be an absolute path, got: " + raw);
        }
        return Optional.of(p.normalize());
    }

    /** Test / AE helper: drop classpath cache entries. */
    public static void clearCache() {
        CACHE.clear();
    }

    public static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("prompt id must be non-blank");
        }
        String n = id.trim().replace('\\', '/');
        while (n.startsWith("/")) {
            n = n.substring(1);
        }
        if (n.contains("..") || n.startsWith("/") || n.contains(":")) {
            throw new IllegalArgumentException("illegal prompt id: " + id);
        }
        if (n.endsWith(".md") || n.endsWith(".txt")) {
            n = n.substring(0, n.lastIndexOf('.'));
        }
        if (n.isBlank()) {
            throw new IllegalArgumentException("prompt id must be non-blank");
        }
        return n;
    }

    private static String readOverride(Path dir, String id) {
        for (String ext : new String[] {".md", ".txt"}) {
            Path file = dir.resolve(id + ext).normalize();
            if (!file.startsWith(dir.normalize())) {
                throw new IllegalArgumentException("prompt path escapes override dir: " + id);
            }
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("failed reading override prompt " + file, e);
                }
            }
        }
        throw new IllegalStateException(
                "prompt not found under override dir " + dir + " for id " + id
                        + " (tried .md and .txt)");
    }

    private static String readClasspath(String id) {
        ClassLoader cl = PromptCatalog.class.getClassLoader();
        for (String ext : new String[] {".md", ".txt"}) {
            String resource = "prompts/" + id + ext;
            try (InputStream in = cl.getResourceAsStream(resource)) {
                if (in != null) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalStateException("failed reading classpath prompt " + resource, e);
            }
        }
        throw new IllegalStateException(
                "prompt not found on classpath for id " + id + " (tried prompts/" + id + ".md|.txt)");
    }
}
