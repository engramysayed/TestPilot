package delivery.visual;

import delivery.ir.TcIdentity;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * On-disk layout under project disk root (same as {@code VisualDiffDiskStore}):
 * <pre>
 * visual-baselines/{caseKey}/{viewport}/baseline.png + meta.json
 * visual-diffs/{runId}/{caseKey}/{viewport}/actual.png (+ optional diff.png, result.json)
 * </pre>
 * Keys match Backend {@code VisualDiffDiskStore} validation:
 * {@code ^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$}. Prefer TC display id ({@code TC_1});
 * do not use {@link TcIdentity#storageKey} (~HH) — that is invalid for the portal store.
 */
public final class VisualBaselinePaths {
    public static final String BASELINES_DIR = "visual-baselines";
    public static final String DIFFS_DIR = "visual-diffs";
    public static final String BASELINE_PNG = "baseline.png";
    public static final String META_JSON = "meta.json";
    public static final String ACTUAL_PNG = "actual.png";
    public static final String DIFF_PNG = "diff.png";
    public static final String RESULT_JSON = "result.json";

    /** Same rule as {@code delivery.portal.service.VisualDiffDiskStore}. */
    public static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");

    private VisualBaselinePaths() {
    }

    /**
     * Portal/AE caseKey for a TC. Valid ids like {@code TC_LOGIN} pass through;
     * otherwise non-allowed chars become {@code _}.
     */
    public static String caseKey(String tcId) {
        String id = TcIdentity.displayId(tcId);
        if (id.isEmpty()) {
            return "tc";
        }
        if (KEY.matcher(id).matches()) {
            return id;
        }
        return sanitizeSegment(id, "tc");
    }

    public static String sanitizeViewport(String viewport) {
        return sanitizeSegment(viewport, VisualDiffConfig.DEFAULT_VIEWPORT);
    }

    public static String sanitizeRunId(String runId) {
        return sanitizeSegment(runId, "local");
    }

    public static String sanitizeSegment(String raw, String fallback) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return fallback;
        }
        if (KEY.matcher(v).matches()) {
            return v;
        }
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString();
        while (out.startsWith("_") || out.startsWith("-")) {
            out = out.substring(1);
        }
        if (out.isBlank()) {
            return fallback;
        }
        if (!KEY.matcher(out).matches()) {
            out = "k_" + out;
            if (out.length() > 128) {
                out = out.substring(0, 128);
            }
        }
        return KEY.matcher(out).matches() ? out : fallback;
    }

    public static Path baselinesRoot(Path projectRoot) {
        return projectRoot.resolve(BASELINES_DIR);
    }

    public static Path baselineDir(Path projectRoot, String caseKey, String viewport) {
        return baselinesRoot(projectRoot).resolve(caseKey).resolve(sanitizeViewport(viewport));
    }

    public static Path baselinePng(Path projectRoot, String caseKey, String viewport) {
        return baselineDir(projectRoot, caseKey, viewport).resolve(BASELINE_PNG);
    }

    public static Path baselineMeta(Path projectRoot, String caseKey, String viewport) {
        return baselineDir(projectRoot, caseKey, viewport).resolve(META_JSON);
    }

    public static Path diffsRoot(Path projectRoot) {
        return projectRoot.resolve(DIFFS_DIR);
    }

    public static Path diffDir(Path projectRoot, String runId, String caseKey, String viewport) {
        return diffsRoot(projectRoot)
                .resolve(sanitizeRunId(runId))
                .resolve(caseKey)
                .resolve(sanitizeViewport(viewport));
    }

    public static Path actualPng(Path projectRoot, String runId, String caseKey, String viewport) {
        return diffDir(projectRoot, runId, caseKey, viewport).resolve(ACTUAL_PNG);
    }

    public static Path diffPng(Path projectRoot, String runId, String caseKey, String viewport) {
        return diffDir(projectRoot, runId, caseKey, viewport).resolve(DIFF_PNG);
    }

    public static Path resultJson(Path projectRoot, String runId, String caseKey, String viewport) {
        return diffDir(projectRoot, runId, caseKey, viewport).resolve(RESULT_JSON);
    }

    public static String describe(String caseKey, String viewport) {
        return caseKey + "/" + sanitizeViewport(viewport).toLowerCase(Locale.ROOT);
    }
}
