package delivery.mobile.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-closed gate: illegal ids, dump stem, Thread.sleep in emitted text, blank screenKey.
 */
public final class MobileCodegenSmellCheck {

    private static final Pattern SLEEP_CALL = Pattern.compile("Thread\\.sleep\\s*\\(");

    private MobileCodegenSmellCheck() {}

    public static void verify(Map<String, ScreenAccumulator.ScreenModel> screens) {
        List<String> smells = new ArrayList<>();
        for (Map.Entry<String, ScreenAccumulator.ScreenModel> e : screens.entrySet()) {
            checkScreen(e.getKey(), e.getValue(), smells);
        }
        if (!smells.isEmpty()) {
            throw new IllegalStateException("Mobile codegen smell check failed:\n" + String.join("\n", smells));
        }
    }

    public static void verifyEmittedSource(String path, String source) {
        if (source != null && SLEEP_CALL.matcher(source).find()) {
            throw new IllegalStateException("Mobile codegen smell: Thread.sleep in emitted " + path);
        }
    }

    static boolean isDumpScreenKey(String screenKey) {
        return MobileCodegenNaming.isDumpScreenKey(screenKey);
    }

    private static void checkScreen(String screenKey, ScreenAccumulator.ScreenModel screen, List<String> smells) {
        if (isDumpScreenKey(screenKey) || "Screen".equals(screen.stem())) {
            smells.add(format(screenKey, screen, "dump/blank screen stem", screen.stem()));
        }
        if (!MobileCodegenNaming.isValidJavaIdentifier(screen.screenClassName())) {
            smells.add(format(screenKey, screen, "illegal screen class", screen.screenClassName()));
        }
        String varName = MobileCodegenNaming.safeLocalVarName(screen.screenClassName());
        if (!MobileCodegenNaming.isValidJavaIdentifier(varName)) {
            smells.add(format(screenKey, screen, "illegal screen var", varName));
        }
        Set<String> seen = new HashSet<>();
        for (ScreenAccumulator.LocatorModel loc : screen.locators()) {
            if (!MobileCodegenNaming.isValidJavaIdentifier(loc.fieldName())) {
                smells.add(format(screenKey, screen, "illegal locator field", loc.fieldName()));
            }
            if (!seen.add(loc.fieldName())) {
                smells.add(format(screenKey, screen, "duplicate locator field", loc.fieldName()));
            }
        }
        Set<String> methods = new HashSet<>();
        for (ScreenAccumulator.ActionModel a : screen.actions()) {
            checkMethod(screenKey, screen, a.methodName(), smells, methods);
        }
        for (ScreenAccumulator.AssertModel a : screen.asserts()) {
            checkMethod(screenKey, screen, a.methodName(), smells, methods);
        }
    }

    private static void checkMethod(
            String screenKey, ScreenAccumulator.ScreenModel screen, String name,
            List<String> smells, Set<String> seen) {
        if (!MobileCodegenNaming.isValidJavaIdentifier(name)) {
            smells.add(format(screenKey, screen, "illegal method", name));
        }
        if (!seen.add(name)) {
            smells.add(format(screenKey, screen, "duplicate method", name));
        }
    }

    private static String format(String key, ScreenAccumulator.ScreenModel screen, String kind, String name) {
        return key + " (" + screen.screenClassName() + "): " + kind + " '" + name + "'";
    }
}
