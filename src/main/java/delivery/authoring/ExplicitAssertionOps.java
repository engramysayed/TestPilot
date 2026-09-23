package delivery.authoring;

import delivery.assertions.CaptureCompare;
import delivery.codegen.ProvenStep;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic Excel/IR operations with caller-supplied locators.
 * Ordinary confirm/verify textContains lines are not rewritten.
 */
public final class ExplicitAssertionOps {
    private static final Pattern CAPTURE = Pattern.compile(
            "(?i)^capture\\s+from\\s+(id|css|name|xpath)=(.+?)\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+using\\s+(.+)$");
    private static final Pattern COMPARE = Pattern.compile(
            "(?i)^compare\\s+captured\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+on\\s+(id|css|name|xpath)=(.+?)\\s+using\\s+(.+)$");
    private static final Pattern SIGNED_OUT = Pattern.compile(
            "(?i)^assert\\s+signed-out\\s+on\\s+(id|css|name|xpath)=(.+?)\\s+expected\\s+(.+)$");

    private ExplicitAssertionOps() {
    }

    public record Parsed(
            String assertionType,
            String locatorStrategy,
            String locatorValue,
            String variable,
            String expected,
            String rationale
    ) {
    }

    public static Parsed parse(String line) {
        String text = line == null ? "" : line.trim();
        Matcher capture = CAPTURE.matcher(text);
        if (capture.matches()) {
            return new Parsed(
                    "captureText",
                    capture.group(1).toLowerCase(Locale.ROOT),
                    capture.group(2).trim(),
                    capture.group(3),
                    CaptureCompare.normalizeRule(capture.group(4)),
                    "intent:CAPTURE");
        }
        Matcher compare = COMPARE.matcher(text);
        if (compare.matches()) {
            if (!"exactText".equals(CaptureCompare.normalizeRule(compare.group(4)))) {
                throw new IllegalArgumentException("Explicit comparison requires exact text");
            }
            return new Parsed(
                    "capturedEquals",
                    compare.group(2).toLowerCase(Locale.ROOT),
                    compare.group(3).trim(),
                    compare.group(1),
                    CaptureCompare.normalizeRule(compare.group(4)),
                    "intent:COMPARE_CAPTURED");
        }
        Matcher signedOut = SIGNED_OUT.matcher(text);
        if (signedOut.matches()) {
            return new Parsed(
                    "signedOut",
                    signedOut.group(1).toLowerCase(Locale.ROOT),
                    signedOut.group(2).trim(),
                    "",
                    CaptureCompare.normalizeSignedOutExpected(signedOut.group(3)),
                    "intent:SIGNED_OUT");
        }
        if (text.matches("(?is)^(capture\\s+from|compare\\s+captured|assert\\s+signed-out)\\b.*")) {
            throw new IllegalArgumentException("Malformed explicit assertion; use a nonempty id, css, name or xpath locator and the required operation arguments");
        }
        return null;
    }

    public static boolean isExplicit(String line) {
        return parse(line) != null;
    }

    public static ProvenStep bind(String line, String tcId) {
        Parsed parsed = parse(line);
        if (parsed == null) {
            throw new IllegalArgumentException("not an explicit assertion: " + line);
        }
        return toStep(parsed, tcId);
    }

    public static ProvenStep toStep(Parsed parsed, String tcId) {
        return new ProvenStep(
                tcId == null ? "" : tcId,
                "Page",
                "elementAction",
                "assert",
                parsed.locatorStrategy(),
                parsed.locatorValue(),
                parsed.variable(),
                parsed.assertionType(),
                parsed.expected(),
                true,
                parsed.rationale());
    }
}
