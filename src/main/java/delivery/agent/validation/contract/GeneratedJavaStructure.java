package delivery.agent.validation.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight structure extraction for generated Page/Test sources (no full Java parser). */
public final class GeneratedJavaStructure {
    private static final Pattern LOCATOR_FIELD = Pattern.compile(
            "protected\\s+(?:final\\s+)?By\\s+(\\w+)");
    private static final Pattern PUBLIC_VOID = Pattern.compile(
            "public\\s+void\\s+(\\w+)\\s*\\(");
    private static final Pattern TEST_METHOD = Pattern.compile(
            "@Test(?:\\([^)]*\\))?\\s*public\\s+void\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern PRIVATE_PAGE_FIELD = Pattern.compile(
            "private\\s+([\\w.]+)\\s+(\\w+)\\s*;");

    private GeneratedJavaStructure() {
    }

    public record PageShape(int locatorFields, int actionMethods, int assertMethods, List<String> locatorNames) {
    }

    public record TestShape(
            List<String> testMethods,
            List<String> stubTestMethods,
            String pageFieldType,
            String pageFieldVar,
            int pageVarUsages) {
    }

    public record TestPageField(String pageClass, String varName, int varUsages) {
    }

    public static PageShape inspectPage(String source) {
        if (source == null || source.isBlank()) {
            return new PageShape(0, 0, 0, List.of());
        }
        List<String> locators = new ArrayList<>();
        Matcher loc = LOCATOR_FIELD.matcher(source);
        while (loc.find()) {
            locators.add(loc.group(1));
        }
        int actions = 0;
        int asserts = 0;
        Matcher methods = PUBLIC_VOID.matcher(source);
        while (methods.find()) {
            String name = methods.group(1);
            if ("isLoaded".equals(name) || name.startsWith("login")) {
                continue;
            }
            if (name.startsWith("assert_")) {
                asserts++;
            } else {
                actions++;
            }
        }
        return new PageShape(locators.size(), actions, asserts, List.copyOf(locators));
    }

    public static TestShape inspectTest(String source) {
        if (source == null || source.isBlank()) {
            return new TestShape(List.of(), List.of(), "", "", 0);
        }
        String pageType = "";
        String pageVar = "";
        Matcher priv = PRIVATE_PAGE_FIELD.matcher(source);
        while (priv.find()) {
            String type = priv.group(1);
            if (type.endsWith("_Page")) {
                pageType = type;
                pageVar = priv.group(2);
            }
        }
        List<String> tests = new ArrayList<>();
        List<String> stubs = new ArrayList<>();
        Matcher tm = TEST_METHOD.matcher(source);
        while (tm.find()) {
            String name = tm.group(1);
            tests.add(name);
            int bodyStart = tm.end();
            int bodyEnd = findMatchingBrace(source, bodyStart - 1);
            if (bodyEnd > bodyStart) {
                String body = source.substring(bodyStart, bodyEnd);
                if (isStubBody(body)) {
                    stubs.add(name);
                }
            }
        }
        int usages = 0;
        if (!pageVar.isBlank()) {
            String needle = pageVar + ".";
            int idx = 0;
            while ((idx = source.indexOf(needle, idx)) >= 0) {
                usages++;
                idx += needle.length();
            }
        }
        return new TestShape(List.copyOf(tests), List.copyOf(stubs), pageType, pageVar, usages);
    }

    public static List<TestPageField> inspectTestPageFields(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        List<TestPageField> fields = new ArrayList<>();
        Matcher priv = PRIVATE_PAGE_FIELD.matcher(source);
        while (priv.find()) {
            String type = priv.group(1);
            String var = priv.group(2);
            if (type.endsWith("_Page")) {
                fields.add(new TestPageField(type, var, countVarUsages(source, var)));
            }
        }
        return List.copyOf(fields);
    }

    static int countVarUsages(String source, String varName) {
        if (varName == null || varName.isBlank()) {
            return 0;
        }
        String needle = varName + ".";
        int usages = 0;
        int idx = 0;
        while ((idx = source.indexOf(needle, idx)) >= 0) {
            usages++;
            idx += needle.length();
        }
        return usages;
    }

    static boolean isStubBody(String body) {
        String stripped = body
                .replaceAll("//[^\n]*", "")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("\\s+", "");
        return stripped.isEmpty();
    }

    static int findMatchingBrace(String source, int openBraceIdx) {
        if (openBraceIdx < 0 || openBraceIdx >= source.length() || source.charAt(openBraceIdx) != '{') {
            return -1;
        }
        int depth = 0;
        for (int i = openBraceIdx; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static boolean locatorNameMatchesConvention(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        return lower.endsWith("_locator") || lower.endsWith("_txt_locator")
                || lower.endsWith("_btn_locator") || lower.endsWith("_lnk_locator")
                || lower.endsWith("_lbl_locator") || lower.endsWith("_el_locator");
    }
}