package delivery.portal.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a heal locator proposal to an <em>existing</em> generated page class only.
 * Never creates a new page file or invents a second page class.
 */
public final class HealJavaLocatorPatcher {
    private static final Pattern SIMPLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private HealJavaLocatorPatcher() {
    }

    public record PatchOutcome(Path pageFile, boolean changed, String detail) {
    }

    /**
     * Resolve {@code src/main/java/project/pages/{SimpleName}.java} for pageClass
     * (simple name or {@code project.pages.X}).
     */
    public static Path resolveExistingPageFile(Path projectRoot, String pageClass) {
        String simple = simpleName(pageClass);
        if (simple == null) {
            throw new MissingPageException("Invalid pageClass: " + pageClass);
        }
        Path pagesDir = projectRoot.resolve("src/main/java/project/pages");
        Path file = pagesDir.resolve(simple + ".java");
        if (!Files.isRegularFile(file)) {
            // AE may emit pageName stem; try CodegenNaming page class.
            String named = delivery.codegen.CodegenNaming.pageClassName(pageClass);
            Path alt = pagesDir.resolve(named + ".java");
            if (Files.isRegularFile(alt)) {
                return alt;
            }
            throw new MissingPageException("Generated page class not found: " + simple);
        }
        return file;
    }

    public static PatchOutcome applyLocatorPatch(
            Path projectRoot,
            String pageClass,
            Map<String, Object> locatorBefore,
            Map<String, Object> locatorAfter
    ) throws IOException {
        Path file = resolveExistingPageFile(projectRoot, pageClass);
        String beforeStrategy = str(locatorBefore == null ? null : locatorBefore.get("strategy"));
        String beforeValue = str(locatorBefore == null ? null : locatorBefore.get("value"));
        String afterStrategy = str(locatorAfter == null ? null : locatorAfter.get("strategy"));
        String afterValue = str(locatorAfter == null ? null : locatorAfter.get("value"));
        if (afterStrategy.isBlank() || afterValue.isBlank()) {
            throw new BadPatchException("locatorAfter.strategy and locatorAfter.value are required");
        }

        String original = Files.readString(file, StandardCharsets.UTF_8);
        String patched = patchSource(original, beforeStrategy, beforeValue, afterStrategy, afterValue);
        if (patched.equals(original)) {
            throw new BadPatchException("No matching locator found in page class for accept patch");
        }
        Files.writeString(file, patched, StandardCharsets.UTF_8);
        return new PatchOutcome(file, true, "locator patched in " + file.getFileName());
    }

    static String patchSource(
            String source,
            String beforeStrategy,
            String beforeValue,
            String afterStrategy,
            String afterValue
    ) {
        String afterExpr = "By." + afterStrategy.trim() + "(\"" + javaStringEscape(afterValue) + "\")";

        if (!beforeStrategy.isBlank() && !beforeValue.isBlank()) {
            Pattern byCall = Pattern.compile(
                    "By\\." + Pattern.quote(beforeStrategy.trim()) + "\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\)");
            Matcher m = byCall.matcher(source);
            StringBuffer sb = new StringBuffer();
            boolean replaced = false;
            while (m.find()) {
                String captured = javaStringUnescape(m.group(1));
                if (!replaced && captured.equals(beforeValue)) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(afterExpr));
                    replaced = true;
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                }
            }
            m.appendTail(sb);
            if (replaced) {
                return sb.toString();
            }
        }

        // Fallback: replace first exact quoted beforeValue inside a By.*("...") call.
        if (!beforeValue.isBlank()) {
            Pattern anyBy = Pattern.compile("By\\.([A-Za-z0-9_]+)\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\)");
            Matcher m = anyBy.matcher(source);
            StringBuffer sb = new StringBuffer();
            boolean replaced = false;
            while (m.find()) {
                String captured = javaStringUnescape(m.group(2));
                if (!replaced && captured.equals(beforeValue)) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(afterExpr));
                    replaced = true;
                } else {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                }
            }
            m.appendTail(sb);
            if (replaced) {
                return sb.toString();
            }
        }

        return source;
    }

    static String simpleName(String pageClass) {
        if (pageClass == null || pageClass.isBlank()) {
            return null;
        }
        String raw = pageClass.trim();
        int dot = raw.lastIndexOf('.');
        String simple = dot >= 0 ? raw.substring(dot + 1) : raw;
        if (!SIMPLE_NAME.matcher(simple).matches()) {
            return null;
        }
        String lower = simple.toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\")) {
            return null;
        }
        return simple;
    }

    static String javaStringEscape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String javaStringUnescape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char n = value.charAt(++i);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"', '\\', '\'' -> n;
                    default -> n;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static class MissingPageException extends RuntimeException {
        public MissingPageException(String message) {
            super(message);
        }
    }

    public static class BadPatchException extends RuntimeException {
        public BadPatchException(String message) {
            super(message);
        }
    }
}
