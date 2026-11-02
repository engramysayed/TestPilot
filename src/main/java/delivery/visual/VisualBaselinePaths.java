package delivery.visual;

import delivery.ir.TcIdentity;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * On-disk layout under {@code PortalStore.projectDiskRoot} / {@code ProjectStore.projectRoot}:
 * <pre>
 * visual-baselines/{caseKey}/{viewport}/baseline.png + meta.json
 * visual-diffs/{runId}/{caseKey}/{viewport}/actual.png (+ optional diff.png, result.json)
 * </pre>
 * caseKey uses {@link TcIdentity#storageKey(String)}.
 */
public final class VisualBaselinePaths {
    public static final String BASELINES_DIR = "visual-baselines";
    public static final String DIFFS_DIR = "visual-diffs";
    public static final String BASELINE_PNG = "baseline.png";
    public static final String META_JSON = "meta.json";
    public static final String ACTUAL_PNG = "actual.png";
    public static final String DIFF_PNG = "diff.png";
    public static final String RESULT_JSON = "result.json";

    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    private VisualBaselinePaths() {
    }

    public static String caseKey(String tcId) {
        return TcIdentity.storageKey(tcId);
    }

    public static String sanitizeViewport(String viewport) {
        String v = viewport == null ? "" : viewport.trim();
        if (v.isEmpty()) {
            return VisualDiffConfig.DEFAULT_VIEWPORT;
        }
        // Allow 1280x800 style as well as named desktop/mobile.
        String compact = v.replace(' ', '_');
        if (SAFE_SEGMENT.matcher(compact).matches()) {
            return compact;
        }
        StringBuilder sb = new StringBuilder(compact.length());
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString();
        return out.isBlank() ? VisualDiffConfig.DEFAULT_VIEWPORT : out;
    }

    public static String sanitizeRunId(String runId) {
        String r = runId == null ? "" : runId.trim();
        if (r.isEmpty()) {
            return "local";
        }
        if (SAFE_SEGMENT.matcher(r).matches()) {
            return r;
        }
        return r.replaceAll("[^A-Za-z0-9._-]", "_");
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

    /** Lowercase helper for logs. */
    public static String describe(String caseKey, String viewport) {
        return caseKey + "/" + sanitizeViewport(viewport).toLowerCase(Locale.ROOT);
    }
}
