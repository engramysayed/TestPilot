package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds missing textContains asserts when Excel ExpectedResult asks for overview items/totals
 * that step text only partially covered (e.g. header-only "Checkout: Overview").
 */
public final class ExpectedCoverageEnricher {
    private static final Pattern SAUCE_PRODUCT = Pattern.compile(
            "(?i)\\b(Sauce Labs [A-Za-z0-9][A-Za-z0-9 \\-]{2,40})\\b");

    private ExpectedCoverageEnricher() {
    }

    public static List<ProvenStep> enrich(ManualTestCase tc, List<ProvenStep> proven) {
        if (tc == null || proven == null || proven.isEmpty()) {
            return proven == null ? List.of() : proven;
        }
        String expected = tc.expectedResult() == null ? "" : tc.expectedResult();
        String lower = expected.toLowerCase(Locale.ROOT);
        boolean wantsTotal = lower.contains("total");
        boolean wantsRemaining = lower.contains("remaining")
                || (lower.contains("overview") && lower.contains("item"));
        if (!wantsTotal && !wantsRemaining) {
            return proven;
        }
        if (!looksLikeOverviewContext(expected, proven)) {
            return proven;
        }

        List<ProvenStep> out = new ArrayList<>(proven);
        String page = lastPageName(proven);
        String tcId = tc.tcId() == null ? "" : tc.tcId();

        if (wantsTotal && !alreadyAsserts(out, "Total")) {
            out.add(textAssert(tcId, page, "Total", "expected:overview-total"));
        }
        if (wantsRemaining) {
            for (String product : remainingProducts(tc)) {
                if (!alreadyAsserts(out, product)) {
                    out.add(textAssert(tcId, page, product, "expected:overview-item"));
                }
            }
        }
        return out;
    }

    static boolean looksLikeOverviewContext(String expected, List<ProvenStep> proven) {
        String blob = (expected == null ? "" : expected).toLowerCase(Locale.ROOT);
        if (blob.contains("overview") || blob.contains("checkout")) {
            return true;
        }
        for (ProvenStep s : proven) {
            String p = s.pageName() == null ? "" : s.pageName().toLowerCase(Locale.ROOT);
            if (p.contains("checkout") || p.contains("overview")) {
                return true;
            }
        }
        return false;
    }

    static Set<String> remainingProducts(ManualTestCase tc) {
        Set<String> added = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        String steps = tc == null || tc.steps() == null ? "" : tc.steps();
        for (String line : steps.split("\\R")) {
            String lower = line.toLowerCase(Locale.ROOT);
            Matcher m = SAUCE_PRODUCT.matcher(line);
            while (m.find()) {
                String name = m.group(1).trim();
                if (lower.contains("remove")) {
                    removed.add(name);
                } else if (lower.contains("add")) {
                    added.add(name);
                }
            }
        }
        added.removeAll(removed);
        return added;
    }

    private static boolean alreadyAsserts(List<ProvenStep> steps, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return true;
        }
        String want = phrase.toLowerCase(Locale.ROOT);
        for (ProvenStep s : steps) {
            String type = s.assertionType() == null ? "" : s.assertionType();
            if (!"textContains".equalsIgnoreCase(type) && !"assert".equalsIgnoreCase(s.action())) {
                continue;
            }
            String exp = s.assertionExpected() == null ? "" : s.assertionExpected().toLowerCase(Locale.ROOT);
            if (exp.contains(want)) {
                return true;
            }
        }
        return false;
    }

    private static String lastPageName(List<ProvenStep> proven) {
        for (int i = proven.size() - 1; i >= 0; i--) {
            ProvenStep s = proven.get(i);
            String p = s.pageName();
            if (p != null && !p.isBlank()) {
                if ("assert".equalsIgnoreCase(s.action())
                        || (s.assertionType() != null && !s.assertionType().isBlank())) {
                    return p;
                }
            }
        }
        for (int i = proven.size() - 1; i >= 0; i--) {
            String p = proven.get(i).pageName();
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return "CheckoutStepTwo";
    }

    private static ProvenStep textAssert(String tcId, String page, String phrase, String rationale) {
        String lit = delivery.authoring.XpathLiterals.quote(phrase == null ? "" : phrase);
        String xpath = "//body//*[not(self::script)][not(self::style)][not(self::noscript)]"
                + "[contains(normalize-space(.)," + lit + ")]"
                + "[not(.//*[contains(normalize-space(.)," + lit + ")])]";
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "xpath", xpath, "", "textContains", phrase, true, rationale);
    }
}
