package delivery.codegen;

import delivery.heal.HealProposal;
import delivery.portal.service.HealJavaLocatorPatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Codegen-side alignment for Phase 2 heal Accept/Reject.
 * Ensures Accept patches an <em>existing</em> generated page class only (never invents
 * {@code LoginPage2} / never creates a missing class). Reuses {@link HealJavaLocatorPatcher}
 * and the Phase 2 proposal store shape — does not fork a second proposal store.
 */
public final class HealAcceptPatcher {
    private static final Pattern INVENTED_SUFFIX = Pattern.compile(
            "^.+(Page)?[_]?2$", Pattern.CASE_INSENSITIVE);

    private HealAcceptPatcher() {
    }

    public record AlignResult(
            String pageClass,
            Path pageFile,
            boolean ok,
            String detail
    ) {
    }

    /**
     * Collapse Login aliases onto the emitted page class name and reject invented *2 names.
     */
    public static String alignPageClass(String pageClass) {
        if (pageClass == null || pageClass.isBlank()) {
            throw new HealJavaLocatorPatcher.MissingPageException("pageClass required");
        }
        String raw = pageClass.trim();
        int dot = raw.lastIndexOf('.');
        String simple = dot >= 0 ? raw.substring(dot + 1) : raw;
        if (looksInvented(simple)) {
            throw new HealJavaLocatorPatcher.BadPatchException(
                    "Refusing invented page class (no LoginPage2): " + simple);
        }
        String stem = simple;
        if (stem.endsWith("_Page")) {
            stem = stem.substring(0, stem.length() - "_Page".length());
        } else if (stem.endsWith("Page") && stem.length() > 4) {
            stem = stem.substring(0, stem.length() - "Page".length());
        }
        ProvenStep probe = new ProvenStep(
                "TC_HEAL", stem, "elementAction", "click",
                "id", "x", "", "", "", true, "heal-align");
        String key = PageAccumulator.pageKey(probe);
        return CodegenNaming.pageClassName(key);
    }

    public static boolean looksInvented(String pageClass) {
        if (pageClass == null || pageClass.isBlank()) {
            return true;
        }
        String simple = pageClass.trim();
        int dot = simple.lastIndexOf('.');
        if (dot >= 0) {
            simple = simple.substring(dot + 1);
        }
        String lower = simple.toLowerCase(Locale.ROOT);
        if (lower.contains("loginpage2") || lower.contains("login_page2") || lower.contains("login_2_page")) {
            return true;
        }
        return INVENTED_SUFFIX.matcher(simple).matches()
                && lower.contains("login");
    }

    public static String simpleName(String pageClass) {
        if (pageClass == null || pageClass.isBlank()) {
            return "";
        }
        String raw = pageClass.trim();
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }

    /**
     * Resolve existing page file under {@code src/main/java/project/pages}; 409-equivalent if missing.
     */
    public static AlignResult resolveExisting(Path projectRoot, String pageClass) {
        String aligned = alignPageClass(pageClass);
        try {
            Path file = HealJavaLocatorPatcher.resolveExistingPageFile(projectRoot, aligned);
            return new AlignResult(aligned, file, true, "ok");
        } catch (HealJavaLocatorPatcher.MissingPageException ex) {
            try {
                Path file = HealJavaLocatorPatcher.resolveExistingPageFile(projectRoot, pageClass);
                return new AlignResult(simpleName(pageClass), file, true, "ok-raw");
            } catch (RuntimeException ignored) {
                return new AlignResult(aligned, null, false,
                        "409: generated page class not found: " + aligned);
            }
        }
    }

    /**
     * Accept: patch locator in the named existing page class only.
     */
    public static HealJavaLocatorPatcher.PatchOutcome accept(
            Path projectRoot,
            HealProposal proposal
    ) throws IOException {
        if (proposal == null) {
            throw new HealJavaLocatorPatcher.BadPatchException("proposal required");
        }
        if (looksInvented(proposal.pageClass())) {
            throw new HealJavaLocatorPatcher.BadPatchException(
                    "Refusing inventing page class on Accept: " + proposal.pageClass());
        }
        AlignResult align = resolveExisting(projectRoot, proposal.pageClass());
        if (!align.ok()) {
            throw new HealJavaLocatorPatcher.MissingPageException(align.detail());
        }
        Map<String, Object> before = locatorMap(proposal.locatorBefore());
        Map<String, Object> after = locatorMap(proposal.locatorAfter());
        return HealJavaLocatorPatcher.applyLocatorPatch(
                projectRoot, align.pageClass(), before, after);
    }

    /**
     * Build a Phase 2 proposal map for AE emit (status=pending). Never rewrites Java.
     */
    public static Map<String, Object> proposalShell(
            String proposalId,
            String projectId,
            String jobId,
            String tcId,
            int stepIndex,
            String pageClass,
            String beforeStrategy,
            String beforeValue,
            String afterStrategy,
            String afterValue,
            String reason
    ) {
        String aligned;
        try {
            aligned = alignPageClass(pageClass);
        } catch (RuntimeException ex) {
            aligned = pageClass == null ? "" : pageClass;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("proposalId", proposalId == null ? "" : proposalId);
        m.put("projectId", projectId == null ? "" : projectId);
        m.put("jobId", jobId == null ? "" : jobId);
        m.put("tcId", tcId == null ? "" : tcId);
        m.put("stepIndex", stepIndex);
        m.put("pageClass", aligned);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("strategy", beforeStrategy == null ? "" : beforeStrategy);
        before.put("value", beforeValue == null ? "" : beforeValue);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("strategy", afterStrategy == null ? "" : afterStrategy);
        after.put("value", afterValue == null ? "" : afterValue);
        m.put("locatorBefore", before);
        m.put("locatorAfter", after);
        m.put("patchKind", "locator");
        m.put("tier", "codegen-align");
        m.put("reason", reason == null ? "" : reason);
        m.put("status", "pending");
        m.put("createdAt", java.time.Instant.now().toString());
        m.put("decidedAt", null);
        m.put("evidenceRel", null);
        return m;
    }

    public static boolean pageClassExists(Path projectRoot, String pageClass) {
        AlignResult r = resolveExisting(projectRoot, pageClass);
        return r.ok() && r.pageFile() != null && Files.isRegularFile(r.pageFile());
    }

    private static Map<String, Object> locatorMap(HealProposal.LocatorRef ref) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (ref == null) {
            m.put("strategy", "");
            m.put("value", "");
            return m;
        }
        m.put("strategy", ref.strategy());
        m.put("value", ref.value());
        return m;
    }
}
