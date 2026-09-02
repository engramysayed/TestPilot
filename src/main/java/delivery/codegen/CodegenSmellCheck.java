package delivery.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-closed gate at emit time: reject pages with collapsed control names or duplicate methods.
 */
public final class CodegenSmellCheck {
    private static final Pattern COLLAPSED_ACTION = Pattern.compile(
            "^(type|select|click)_?(type|select|click|assert)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLAPSED_ASSERT = Pattern.compile(
            "^assert_assert_",
            Pattern.CASE_INSENSITIVE);

    private CodegenSmellCheck() {
    }

    public static void verify(Map<String, PageAccumulator.PageModel> pages) {
        List<String> smells = new ArrayList<>();
        for (Map.Entry<String, PageAccumulator.PageModel> entry : pages.entrySet()) {
            checkPage(entry.getKey(), entry.getValue(), smells);
        }
        if (!smells.isEmpty()) {
            throw new IllegalStateException("Codegen smell check failed:\n" + String.join("\n", smells));
        }
    }

    private static void checkPage(String pageName, PageAccumulator.PageModel page, List<String> smells) {
        Set<String> seen = new HashSet<>();
        for (PageAccumulator.MethodModel method : page.methods()) {
            checkMethodName(pageName, method.name(), smells, seen);
        }
        for (PageAccumulator.AssertionModel assertion : page.assertions()) {
            checkMethodName(pageName, assertion.name(), smells, seen);
            if (COLLAPSED_ASSERT.matcher(assertion.name()).find()) {
                smells.add(formatSmell(pageName, "collapsed assert method", assertion.name()));
            }
        }
    }

    private static void checkMethodName(
            String pageName, String name, List<String> smells, Set<String> seen) {
        if (COLLAPSED_ACTION.matcher(name).matches()) {
            smells.add(formatSmell(pageName, "collapsed action method", name));
        }
        if (!seen.add(name)) {
            smells.add(formatSmell(pageName, "duplicate method", name));
        }
    }

    private static String formatSmell(String pageName, String kind, String name) {
        return pageName + " (" + pageDisplayName(pageName) + "): " + kind + " '" + name + "'";
    }

    private static String pageDisplayName(String pageName) {
        return CodegenNaming.actionsClassName(pageName);
    }
}
