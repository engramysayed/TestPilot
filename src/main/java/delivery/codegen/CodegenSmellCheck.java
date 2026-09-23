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
        if (!CodegenNaming.isValidJavaIdentifier(page.stem())) {
            smells.add(formatSmell(pageName, page, "illegal page stem", page.stem()));
        }
        if (!CodegenNaming.isValidJavaIdentifier(page.actionsClassName())) {
            smells.add(formatSmell(pageName, page, "illegal actions class", page.actionsClassName()));
        }
        if (!CodegenNaming.isValidJavaIdentifier(page.locatorsClassName())) {
            smells.add(formatSmell(pageName, page, "illegal locators class", page.locatorsClassName()));
        }
        String varName = CodegenNaming.safeLocalVarName(page.actionsClassName());
        if (!CodegenNaming.isValidJavaIdentifier(varName)) {
            smells.add(formatSmell(pageName, page, "illegal page var", varName));
        }
        for (PageAccumulator.FieldModel field : page.fields()) {
            if (!CodegenNaming.isValidJavaIdentifier(field.name())) {
                smells.add(formatSmell(pageName, page, "illegal field name", field.name()));
            }
        }
        Set<String> seen = new HashSet<>();
        for (PageAccumulator.MethodModel method : page.methods()) {
            checkMethodName(pageName, page, method.name(), smells, seen);
        }
        for (PageAccumulator.AssertionModel assertion : page.assertions()) {
            checkMethodName(pageName, page, assertion.name(), smells, seen);
            if (COLLAPSED_ASSERT.matcher(assertion.name()).find()) {
                smells.add(formatSmell(pageName, page, "collapsed assert method", assertion.name()));
            }
        }
    }

    private static void checkMethodName(
            String pageName, PageAccumulator.PageModel page, String name, List<String> smells, Set<String> seen) {
        if (COLLAPSED_ACTION.matcher(name).matches()) {
            smells.add(formatSmell(pageName, page, "collapsed action method", name));
        }
        if (!CodegenNaming.isValidJavaIdentifier(name)) {
            smells.add(formatSmell(pageName, page, "illegal Java method name", name));
        }
        if (!seen.add(name)) {
            smells.add(formatSmell(pageName, page, "duplicate method", name));
        }
    }

    private static String formatSmell(String pageName, PageAccumulator.PageModel page, String kind, String name) {
        return pageName + " (" + page.actionsClassName() + "): " + kind + " '" + name + "'";
    }
}
