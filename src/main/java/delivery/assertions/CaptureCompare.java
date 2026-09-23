package delivery.assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared capture/compare rules for prove-time and generated replay.
 * Comparison is exact text; a shorter id must not match a longer one.
 */
public final class CaptureCompare {
    private CaptureCompare() {
    }

    public record ExtractResult(boolean ok, String value, String error) {
        public static ExtractResult success(String value) {
            return new ExtractResult(true, value, "");
        }

        public static ExtractResult fail(String error) {
            return new ExtractResult(false, "", error == null ? "capture failed" : error);
        }
    }

    public record CompareResult(boolean ok, String error) {
        public static CompareResult success() {
            return new CompareResult(true, "");
        }

        public static CompareResult fail(String error) {
            return new CompareResult(false, error == null ? "compare failed" : error);
        }
    }

    public static String normalizeRule(String rule) {
        if (rule == null) {
            return "";
        }
        String trimmed = rule.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("regex:")) {
            return "regex:" + trimmed.substring("regex:".length());
        }
        String compact = trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if ("exacttext".equals(compact) || "exact".equals(compact)) {
            return "exactText";
        }
        return trimmed;
    }

    public static ExtractResult extract(List<String> elementTexts, String rule) {
        if (elementTexts == null || elementTexts.isEmpty()) {
            return ExtractResult.fail("missing capture: no elements");
        }
        if (elementTexts.size() > 1) {
            return ExtractResult.fail("ambiguous extraction: " + elementTexts.size() + " elements");
        }
        String text = elementTexts.get(0) == null ? "" : elementTexts.get(0).trim();
        String normalized = normalizeRule(rule);
        if ("exactText".equals(normalized)) {
            if (text.isEmpty()) {
                return ExtractResult.fail("missing capture: empty text");
            }
            return ExtractResult.success(text);
        }
        if (normalized.startsWith("regex:")) {
            String pattern = normalized.substring("regex:".length());
            if (pattern.isBlank()) {
                return ExtractResult.fail("unknown extraction rule: empty regex");
            }
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            List<String> hits = new ArrayList<>();
            while (matcher.find()) {
                String hit = matcher.groupCount() >= 1 && matcher.group(1) != null
                        ? matcher.group(1)
                        : matcher.group();
                hits.add(hit);
            }
            if (hits.size() != 1) {
                return ExtractResult.fail("ambiguous extraction: " + hits.size() + " regex matches");
            }
            return ExtractResult.success(hits.get(0));
        }
        return ExtractResult.fail("unknown extraction rule: " + rule);
    }

    public static boolean anyExactEquals(List<String> texts, String expected) {
        if (expected == null) {
            return false;
        }
        if (texts == null) {
            return false;
        }
        for (String text : texts) {
            String actual = text == null ? "" : text.trim();
            if (expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    public static CompareResult compareExact(List<String> texts, String captured) {
        if (captured == null || captured.isBlank()) {
            return CompareResult.fail("missing capture");
        }
        if (texts == null || texts.isEmpty()) {
            return CompareResult.fail("no comparison elements");
        }
        if (anyExactEquals(texts, captured)) {
            return CompareResult.success();
        }
        return CompareResult.fail("no element text equals captured value");
    }

    public static String normalizeSignedOutExpected(String expected) {
        if (expected == null) {
            return "";
        }
        String trimmed = expected.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("empty") || lower.equals("not visible") || lower.equals("notvisible")
                || lower.equals("blank")) {
            return "empty";
        }
        if (lower.startsWith("text:")) {
            return "text:" + trimmed.substring("text:".length()).trim();
        }
        if (lower.startsWith("text ")) {
            return "text:" + trimmed.substring("text ".length()).trim();
        }
        return trimmed;
    }

    public static CompareResult signedOut(List<String> displayedTexts, boolean anyDisplayed, String expected) {
        String want = normalizeSignedOutExpected(expected);
        if ("empty".equals(want)) {
            if (!anyDisplayed) {
                return CompareResult.success();
            }
            for (String text : displayedTexts == null ? List.<String>of() : displayedTexts) {
                if (text != null && !text.isBlank()) {
                    return CompareResult.fail("signed-out expected empty, live text was present");
                }
            }
            return CompareResult.success();
        }
        if (want.startsWith("text:")) {
            String expectedText = want.substring("text:".length());
            if (!anyDisplayed) {
                return CompareResult.fail("signed-out expected text but locator was not visible");
            }
            if (anyExactEquals(displayedTexts, expectedText)) {
                return CompareResult.success();
            }
            return CompareResult.fail("signed-out text did not equal '" + expectedText + "'");
        }
        return CompareResult.fail("unknown signed-out expected: " + expected);
    }
}
