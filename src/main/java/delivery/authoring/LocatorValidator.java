package delivery.authoring;

import java.util.Locale;
import java.util.regex.Pattern;

public class LocatorValidator {
    private static final Pattern UUID_LIKE = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LONG_DIGITS = Pattern.compile(".*\\d{6,}.*");
    /** tag[attr='value'], optionally narrowed by further [attr='value'] groups */
    private static final Pattern CSS_ATTR = Pattern.compile(
            "^[a-zA-Z][\\w-]*(\\[[\\w-]+\\s*=\\s*['\"][^'\"]+['\"]\\])+$");
    /** //tag[@attr='value'] with optional and @attr=... */
    private static final Pattern XPATH_ATTR = Pattern.compile(
            "^//[a-zA-Z][\\w-]*\\[@[\\w-]+\\s*=\\s*['\"][^'\"]+['\"](\\s+and\\s+@[\\w-]+\\s*=\\s*['\"][^'\"]+['\"])*\\]$");
    /** //tag[contains(normalize-space(.),'Text')] plus the optional innermost-node predicate */
    private static final Pattern XPATH_TEXT = Pattern.compile(
            "^//[a-zA-Z][\\w-]*\\[contains\\(normalize-space\\(\\.\\),\\s*'[^']+'\\)\\]"
                    + "(\\[not\\(\\.//\\*\\[contains\\(normalize-space\\(\\.\\),\\s*'[^']+'\\)\\]\\)\\])?$");
    /**
     * A control anchored on the label a user reads:
     * {@code //label[normalize-space(.)='First name']//input} for a wrapping label,
     * {@code …/following-sibling::input[1]} when the control is the next sibling,
     * {@code …/following::input[1]} only when that axis really selects this control, or
     * {@code //input[@id=//label[...]/@for]} when the label uses {@code for}.
     */
    private static final Pattern XPATH_LABEL_ANCHORED = Pattern.compile(
            "^//label\\[(?:normalize-space\\(\\.\\)\\s*=\\s*'[^']+'"
                    + "|contains\\(normalize-space\\(\\.\\),\\s*'[^']+'\\))\\]"
                    + "(//|/following::|/following-sibling::)[a-zA-Z][\\w-]*(\\[\\d+\\])?$");
    private static final Pattern XPATH_LABEL_FOR = Pattern.compile(
            "^//[a-zA-Z][\\w-]*\\[@id=//label\\[normalize-space\\(\\.\\)\\s*=\\s*'[^']+'\\]/@for\\]$");
    /**
     * (//input)[3] — a document-order ordinal, allowed only for form controls. Positional matching
     * on a generic container stays rejected: the third div means nothing, whereas the third input
     * on a form is the last way to reach a field that carries no name of its own.
     */
    private static final Pattern XPATH_INDEXED = Pattern.compile(
            "^\\(//(input|select|textarea)(\\[@[\\w-]+\\s*=\\s*'[^']+'\\])?\\)\\[\\d+\\]$");

    public ValidationResult validate(LocatorCandidate candidate) {
        if (candidate == null) {
            return ValidationResult.reject("locator is null");
        }
        String strategy = safe(candidate.strategy()).toLowerCase(Locale.ROOT);
        String value = safe(candidate.value());
        if (value.isBlank()) {
            return ValidationResult.reject("locator value is blank");
        }
        if (value.startsWith("/html") || value.startsWith("/HTML") || value.startsWith("//html")) {
            return ValidationResult.reject("absolute /html xpath is not allowed");
        }
        if ("id".equals(strategy) || "name".equals(strategy)) {
            if (UUID_LIKE.matcher(value).find() || LONG_DIGITS.matcher(value).matches()) {
                return ValidationResult.reject("dynamic id/name pattern rejected");
            }
            if (GeneratedIdDetector.looksGenerated(value)) {
                return ValidationResult.reject("framework-generated id/name rejected: " + value);
            }
            return ValidationResult.ok();
        }
        if (isDataTestStrategy(strategy)) {
            if (UUID_LIKE.matcher(value).find()) {
                return ValidationResult.reject("dynamic data-test id rejected");
            }
            if (GeneratedIdDetector.looksGenerated(value)) {
                return ValidationResult.reject("framework-generated data-test rejected: " + value);
            }
            return ValidationResult.ok();
        }
        if ("css".equals(strategy) || "cssselector".equals(strategy)) {
            if (value.contains(":")) {
                return ValidationResult.reject("CSS pseudo-classes are not allowed");
            }
            if (UUID_LIKE.matcher(value).find() || LONG_DIGITS.matcher(value).matches()) {
                return ValidationResult.reject("dynamic css pattern rejected");
            }
            if (!CSS_ATTR.matcher(value).matches()) {
                return ValidationResult.reject("CSS must be tag[attribute='value'] form");
            }
            return ValidationResult.ok();
        }
        if ("xpath".equals(strategy)) {
            if (value.startsWith("/html") || value.startsWith("//html")) {
                return ValidationResult.reject("absolute html xpath rejected");
            }
            // D15: body-scoped textContains xpath is a first-class allowlisted strategy
            if (isBodyScopedTextXpath(value)) {
                return ValidationResult.ok();
            }
            // A button whose only identity is its label needs a tag-scoped text match
            if (XPATH_TEXT.matcher(value).matches()) {
                return ValidationResult.ok();
            }
            if (XPATH_LABEL_ANCHORED.matcher(value).matches()) {
                return ValidationResult.ok();
            }
            if (XPATH_LABEL_FOR.matcher(value).matches()) {
                return ValidationResult.ok();
            }
            if (XPATH_INDEXED.matcher(value).matches()) {
                return ValidationResult.ok();
            }
            if (!XPATH_ATTR.matcher(value).matches()) {
                return ValidationResult.reject("XPath must be //tag[@attribute='value'] form");
            }
            return ValidationResult.ok();
        }
        return ValidationResult.reject("unsupported locator strategy: " + strategy);
    }

    /** Matches StepIntentBinder.xpathContainsText / notVisible control xpath shapes. */
    static boolean isBodyScopedTextXpath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if (v.startsWith("//body//*[") && v.contains("contains(normalize-space(.),")) {
            return true;
        }
        // notVisible control xpath from binder
        return v.startsWith("//*[self::button or self::a or self::input")
                && v.contains("contains(normalize-space(.),");
    }

    private static boolean isDataTestStrategy(String strategy) {
        return "data-testid".equals(strategy)
                || "data-test".equals(strategy)
                || "data-qa".equals(strategy)
                || "testid".equals(strategy);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
