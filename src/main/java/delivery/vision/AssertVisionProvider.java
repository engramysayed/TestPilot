package delivery.vision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Assertion vision model selection (A6, aligned with Backend SPLIT_DESIGN).
 * Key {@value #KEY} = {@code auto | uitars | qwen} (default {@code uitars}):
 * <ul>
 *   <li>{@code uitars}: UI-TARS ("ui-tars").</li>
 *   <li>{@code qwen}: Qwen2.5-VL 7B ({@value #QWEN7B_MODEL}, Apache-2.0) via Ollama.</li>
 *   <li>{@code auto}: qwen only when GPU VRAM &gt;= {@value #AUTO_MIN_VRAM_GB} GB, else uitars; if VRAM is unknown, uitars (logged).</li>
 * </ul>
 * Deprecated (warned): key {@value #ALIAS_KEY}, value {@code qwen7b}.
 * Precedence: per-job override &gt; -D (new key &gt; alias &gt; legacy umbrella delivery.vision.provider)
 * &gt; application.properties (same order) &gt; default. Unknown values are a configuration error.
 */
public enum AssertVisionProvider {
    QWEN("qwen", "qwen2.5vl:7b"),
    UITARS("uitars", "ui-tars");

    public static final String KEY = "delivery.vision.assert.provider";
    /** @deprecated use {@link #KEY}. */
    @Deprecated public static final String ALIAS_KEY = "keel.assert.vision.provider";
    public static final String VRAM_HINT_KEY = "delivery.vision.gpu.vram-gb";
    public static final String QWEN7B_MODEL = "qwen2.5vl:7b";
    public static final AssertVisionProvider DEFAULT = UITARS;
    public static final double AUTO_MIN_VRAM_GB = 12.0;

    private static final Logger LOG = LoggerFactory.getLogger(AssertVisionProvider.class);
    private static final ThreadLocal<String> OVERRIDE = new ThreadLocal<>();

    /** Probe used when no VRAM hint is configured; replaceable in tests. Result cached per JVM. */
    static volatile Supplier<OptionalDouble> vramProbe = AssertVisionProvider::nvidiaSmiVramGb;
    private static volatile OptionalDouble probed;

    public final String engineId;
    public final String defaultModel;

    AssertVisionProvider(String engineId, String defaultModel) {
        this.engineId = engineId;
        this.defaultModel = defaultModel;
    }

    /** Validates and canonicalises a setting: returns "auto", "uitars" or "qwen". */
    static String normalize(String raw, String key) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        switch (v) {
            case "auto", "uitars", "qwen": return v;
            case "qwen7b":
                LOG.warn("{}=qwen7b is deprecated; use qwen", key);
                return "qwen";
            default:
                throw new IllegalArgumentException(key + "='" + raw + "' is not supported; use auto, uitars or qwen");
        }
    }

    /** Explicit provider (auto is resolved). */
    public static AssertVisionProvider parse(String raw) {
        return fromSetting(normalize(raw, KEY), System::getProperty, k -> null);
    }

    /**
     * Setting from one property source (new key, deprecated alias, legacy umbrella), or null when
     * nothing is set there. Unknown umbrella values are ignored (that key also drives grounding).
     */
    static String settingOrNull(Function<String, String> props) {
        String v = props.apply(KEY);
        if (v != null && !v.isBlank()) return normalize(v, KEY);
        String alias = props.apply(ALIAS_KEY);
        if (alias != null && !alias.isBlank()) {
            LOG.warn("{} is deprecated; use {}", ALIAS_KEY, KEY);
            return normalize(alias, ALIAS_KEY);
        }
        String umbrella = props.apply("delivery.vision.provider");
        if (umbrella == null) return null;
        String u = umbrella.trim().toLowerCase(Locale.ROOT);
        return u.equals("uitars") || u.equals("qwen") ? u : u.equals("qwen7b") ? "qwen" : null;
    }

    /** Full resolution: override &gt; system &gt; file &gt; default. */
    static AssertVisionProvider resolve(Function<String, String> system, Function<String, String> file) {
        String s = OVERRIDE.get();
        if (s == null) s = settingOrNull(system);
        if (s == null) s = settingOrNull(file);
        if (s == null) return DEFAULT;
        return fromSetting(s, system, file);
    }

    /**
     * Production lookups. utils.PropertyReader copies application.properties INTO System properties, so a
     * plain System.getProperty cannot tell -D from the file; that let the file's new key silently beat a
     * -D alias. "Runtime" therefore means a System property that is absent from, or differs from, the file.
     */
    static AssertVisionProvider resolveConfigured() {
        return resolve(AssertVisionProvider::runtimeProp, AssertVisionProvider::fileProp);
    }

    private static volatile java.util.Properties fileProps;

    static String fileProp(String key) {
        java.util.Properties p = fileProps;
        if (p == null) {
            p = new java.util.Properties();
            try (java.io.InputStream in = AssertVisionProvider.class.getResourceAsStream("/application.properties")) {
                if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                LOG.warn("cannot read application.properties: {}", e.toString());
            }
            fileProps = p;
        }
        return p.getProperty(key);
    }

    static String runtimeProp(String key) {
        String v = System.getProperty(key);
        if (v == null) return null;
        String f = fileProp(key);
        return f != null && f.trim().equals(v.trim()) ? null : v;
    }

    /** Single-source convenience (tests). */
    static AssertVisionProvider resolve(Function<String, String> props) {
        return resolve(props, k -> null);
    }

    static AssertVisionProvider fromSetting(String s, Function<String, String> system, Function<String, String> file) {
        return switch (s) {
            case "qwen" -> QWEN;
            case "uitars" -> UITARS;
            default -> auto(system, file);
        };
    }

    static AssertVisionProvider auto(Function<String, String> system, Function<String, String> file) {
        OptionalDouble vram = vramGb(system, file);
        if (vram.isEmpty()) {
            LOG.warn("{}=auto: GPU VRAM unknown (set {} to override detection); using uitars", KEY, VRAM_HINT_KEY);
            return UITARS;
        }
        AssertVisionProvider p = vram.getAsDouble() >= AUTO_MIN_VRAM_GB ? QWEN : UITARS;
        LOG.info("{}=auto: GPU VRAM {} GB -> {}", KEY, vram.getAsDouble(), p.engineId);
        return p;
    }

    static OptionalDouble vramGb(Function<String, String> system, Function<String, String> file) {
        for (Function<String, String> src : java.util.List.of(system, file)) {
            String h = src.apply(VRAM_HINT_KEY);
            if (h != null && !h.isBlank()) {
                try {
                    return OptionalDouble.of(Double.parseDouble(h.trim()));
                } catch (NumberFormatException e) {
                    LOG.warn("{}='{}' is not a number; ignoring", VRAM_HINT_KEY, h);
                }
            }
        }
        OptionalDouble cached = probed;
        if (cached == null) {
            try {
                cached = vramProbe.get();
            } catch (RuntimeException e) {
                LOG.warn("GPU VRAM probe failed: {}", e.toString());
                cached = OptionalDouble.empty();
            }
            probed = cached;
        }
        return cached;
    }

    /** Test hook: forget the cached probe result. */
    static void resetProbeCache() {
        probed = null;
    }

    /** Largest GPU's total memory via {@code nvidia-smi} (2 s timeout); empty when absent or unparsable. */
    static OptionalDouble nvidiaSmiVramGb() {
        try {
            Process p = new ProcessBuilder("nvidia-smi", "--query-gpu=memory.total", "--format=csv,noheader,nounits")
                    .redirectErrorStream(true).start();
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return OptionalDouble.empty();
            }
            double maxMib = -1;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    try { maxMib = Math.max(maxMib, Double.parseDouble(line.trim())); } catch (NumberFormatException ignored) { }
                }
            }
            return p.exitValue() == 0 && maxMib > 0 ? OptionalDouble.of(maxMib / 1024.0) : OptionalDouble.empty();
        } catch (Exception e) {
            return OptionalDouble.empty();
        } finally {
            if (Thread.interrupted()) Thread.currentThread().interrupt();
        }
    }

    /**
     * Per-job / per-project override for the current worker thread ({@code auto|uitars|qwen}); beats
     * every property. A null/blank value means "no override". Nestable.
     */
    public static Scope withOverride(String value) {
        String previous = OVERRIDE.get();
        if (value == null || value.isBlank()) OVERRIDE.remove(); else OVERRIDE.set(normalize(value, "assert provider override"));
        return () -> { if (previous == null) OVERRIDE.remove(); else OVERRIDE.set(previous); };
    }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}
