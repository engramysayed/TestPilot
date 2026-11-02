package delivery.job;

import delivery.excel.ManualTestCase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Phase 3 AE: honor suite {@code tcIds} / tag / suiteId when selecting cases for execute/prove.
 * Empty filter = all cases (caller's prior KeelPath filter still applies).
 */
public final class SuiteCaseFilter {
    private SuiteCaseFilter() {
    }

    /**
     * Apply active {@link SuiteRunContext} (or resolve from suites.json via projectRoot on the request).
     * Returns {@code cases} unchanged when no suite filter is active.
     */
    public static List<ManualTestCase> apply(
            List<ManualTestCase> cases,
            Path projectRoot
    ) {
        SuiteRunContext ctx = SuiteRunContext.current();
        String suiteId = ctx == null ? null : ctx.suiteId();
        String tag = ctx == null ? null : ctx.tag();
        List<String> tcIds = ctx == null ? null : ctx.tcIds();
        if ((tcIds == null || tcIds.isEmpty()) && projectRoot != null
                && ((suiteId != null && !suiteId.isBlank()) || (tag != null && !tag.isBlank()))) {
            tcIds = SuiteStore.resolveTcIds(projectRoot, suiteId, tag);
        }
        return filter(cases, tcIds, tag, projectRoot != null && suiteId == null && (tcIds == null || tcIds.isEmpty()));
    }

    /**
     * @param tcIds explicit allow-list; null/empty means no tcId restriction
     * @param caseTagFallback when true and tag set with empty tcIds, match {@link ManualTestCase#tags()}
     */
    public static List<ManualTestCase> filter(
            List<ManualTestCase> cases,
            Collection<String> tcIds,
            String tag,
            boolean caseTagFallback
    ) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        Set<String> allow = normalizeIds(tcIds);
        String wantTag = tag == null || tag.isBlank() ? null : tag.trim().toLowerCase(Locale.ROOT);
        if (allow.isEmpty() && wantTag == null) {
            return List.copyOf(cases);
        }
        if (!allow.isEmpty()) {
            List<ManualTestCase> out = new ArrayList<>();
            for (ManualTestCase tc : cases) {
                if (tc != null && tc.tcId() != null && allow.contains(tc.tcId().trim())) {
                    out.add(tc);
                }
            }
            return out;
        }
        if (caseTagFallback && wantTag != null) {
            List<ManualTestCase> out = new ArrayList<>();
            for (ManualTestCase tc : cases) {
                if (tc != null && caseHasTag(tc.tags(), wantTag)) {
                    out.add(tc);
                }
            }
            return out;
        }
        // tag/suite resolved to empty tcIds → no matching cases
        return List.of();
    }

    public static List<ManualTestCase> requireFiltered(
            List<ManualTestCase> cases,
            Path projectRoot,
            String emptyMessage
    ) {
        List<ManualTestCase> filtered = apply(cases, projectRoot);
        if (filtered.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage == null || emptyMessage.isBlank()
                    ? "No test cases match the suite tag/suiteId filter"
                    : emptyMessage);
        }
        return filtered;
    }

    static boolean caseHasTag(String rawTags, String wantLower) {
        if (rawTags == null || rawTags.isBlank() || wantLower == null) {
            return false;
        }
        for (String part : rawTags.split("[,;|/]")) {
            if (wantLower.equals(part.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizeIds(Collection<String> tcIds) {
        Set<String> allow = new LinkedHashSet<>();
        if (tcIds == null) {
            return allow;
        }
        for (String id : tcIds) {
            if (id != null && !id.isBlank()) {
                allow.add(id.trim());
            }
        }
        return allow;
    }
}
