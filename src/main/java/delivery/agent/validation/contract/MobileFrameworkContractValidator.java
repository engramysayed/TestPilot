package delivery.agent.validation.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lightweight validator for emitted mobile code (Screens + Tests). Mirrors the web
 * {@link FrameworkContractValidator} for the subset of rules that have a direct mobile analogue —
 * see {@code docs/MOBILE_FRAMEWORK_RULES.md} §1, §3, §7.
 *
 * <p>Each finding carries an {@code FW_RULE_M_*} code so the doc + validator stay in sync via
 * {@link MobileFrameworkRulesDocSyncTest}. This is on purpose not a 1:1 port of the web validator:
 * big-picture rules the mobile pipeline enforces elsewhere (page clustering, test ↔ page import
 * matching) are not checked here yet.
 */
public final class MobileFrameworkContractValidator {

    // FW_RULE_M_APPIUM_ONLY   — only Appium / AndroidDriver imports allowed in a Screen.
    // FW_RULE_M_NO_CSS        — By.cssSelector is never valid in NATIVE_APP context.
    // FW_RULE_M_NO_SLEEP      — no Thread.sleep / TimeUnit.*.sleep inside a Screen.
    // FW_RULE_M_SETTLE_DELEGATE — Screen actions never call driver.findElement directly.
    // FW_RULE_M_TEST_BASE     — Generated test classes extend BaseMobileTest.
    // FW_RULE_M_TEST_NO_BY    — Tests never declare raw By fields (that lives on the Screen).
    // FW_RULE_M_NO_SYSOUT     — no System.out / System.err in either Screen or Test.

    private static final Pattern IMPORT_LINE = Pattern.compile("^\\s*import\\s+([^;\\s]+);");
    private static final Pattern RAW_BY_FIELD = Pattern.compile(
            "\\b(?:private|protected|public)?\\s*(?:static\\s+)?(?:final\\s+)?By\\s+\\w+\\s*=");
    private static final Pattern THREAD_SLEEP = Pattern.compile("\\bThread\\.sleep\\s*\\(");
    private static final Pattern TIMEUNIT_SLEEP = Pattern.compile("\\bTimeUnit\\.[A-Z_]+\\.sleep\\s*\\(");
    private static final Pattern DIRECT_FIND = Pattern.compile("\\bdriver\\.findElement[s]?\\s*\\(");
    private static final Pattern CSS_SELECTOR = Pattern.compile("\\bBy\\.cssSelector\\s*\\(");
    private static final Pattern SYSOUT = Pattern.compile("\\bSystem\\.(?:out|err)\\.");
    private static final Pattern EXTENDS_BASE = Pattern.compile(
            "class\\s+\\w+\\s+extends\\s+BaseMobileTest\\b");

    private static final List<String> ALLOWED_SCREEN_IMPORT_PREFIXES = List.of(
            "io.appium.java_client.",
            "io.qameta.allure.",
            "org.openqa.selenium.",
            "org.testng.",
            "java.",
            "javax.",
            "project.",
            "utils.");

    public static final class Finding {
        public final String code;
        public final Path file;
        public final int line;
        public final String message;
        public final String fix;

        public Finding(String code, Path file, int line, String message, String fix) {
            this.code = code;
            this.file = file;
            this.line = line;
            this.message = message == null ? "" : message;
            this.fix = fix == null ? "" : fix;
        }

        @Override public String toString() {
            String location = file == null ? "?" : file.getFileName().toString();
            return "[" + code + "] " + location + ":" + line + " — " + message;
        }
    }

    private MobileFrameworkContractValidator() {}

    /** Run every mobile rule on each Screen + Test file found under {@code emitRoot}. */
    public static List<Finding> validate(Path emitRoot) throws IOException {
        List<Finding> findings = new ArrayList<>();
        if (emitRoot == null || !Files.isDirectory(emitRoot)) {
            return findings;
        }
        try (Stream<Path> walk = Files.walk(emitRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String name = p.getFileName().toString();
                            if (name.endsWith("Screen.java")) {
                                checkScreen(p, findings);
                            } else if (name.endsWith("Test.java")) {
                                checkTest(p, findings);
                            }
                        } catch (IOException ignored) {
                            // best-effort scan
                        }
                    });
        }
        return findings;
    }

    static void checkScreen(Path file, List<Finding> findings) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNo = i + 1;
            var im = IMPORT_LINE.matcher(line);
            if (im.find()) {
                String imp = im.group(1);
                boolean allowed = ALLOWED_SCREEN_IMPORT_PREFIXES.stream().anyMatch(imp::startsWith);
                if (!allowed) {
                    findings.add(new Finding("FW_RULE_M_APPIUM_ONLY", file, lineNo,
                            "Screen imports non-Appium / non-project package: " + imp,
                            "Mobile screens import only Appium, Selenium, project, and standard-library types."));
                }
            }
            if (CSS_SELECTOR.matcher(line).find()) {
                findings.add(new Finding("FW_RULE_M_NO_CSS", file, lineNo,
                        "By.cssSelector is not valid in NATIVE_APP context",
                        "Use AppiumBy.id / accessibilityId / xpath / androidUIAutomator."));
            }
            if (THREAD_SLEEP.matcher(line).find() || TIMEUNIT_SLEEP.matcher(line).find()) {
                findings.add(new Finding("FW_RULE_M_NO_SLEEP", file, lineNo,
                        "Thread.sleep is banned in Screen classes",
                        "Use MobileWait or SettledMobileActions' built-in settle budget."));
            }
            if (DIRECT_FIND.matcher(line).find()) {
                findings.add(new Finding("FW_RULE_M_SETTLE_DELEGATE", file, lineNo,
                        "Screen actions must go through SettledMobileActions, not driver.findElement",
                        "Call SettledMobileActions.tap / type / scrollUntil / swipe / typeSecret."));
            }
            if (SYSOUT.matcher(line).find()) {
                findings.add(new Finding("FW_RULE_M_NO_SYSOUT", file, lineNo,
                        "System.out / System.err is banned — use LogsManager",
                        "LogsManager.info(...) / .warn(...) / .error(...)."));
            }
        }
    }

    static void checkTest(Path file, List<Finding> findings) throws IOException {
        String body = Files.readString(file);
        List<String> lines = body.lines().toList();
        if (!EXTENDS_BASE.matcher(body).find()) {
            findings.add(new Finding("FW_RULE_M_TEST_BASE", file, 1,
                    "Mobile test must extend BaseMobileTest",
                    "Add 'extends BaseMobileTest' and let it own driver + auth."));
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNo = i + 1;
            if (RAW_BY_FIELD.matcher(line).find()) {
                findings.add(new Finding("FW_RULE_M_TEST_NO_BY", file, lineNo,
                        "Test defines a raw By field; those belong on the Screen class",
                        "Move the By to the matching Screen class."));
            }
            if (SYSOUT.matcher(line).find()) {
                findings.add(new Finding("FW_RULE_M_NO_SYSOUT", file, lineNo,
                        "System.out / System.err is banned — use LogsManager",
                        "LogsManager.info(...) / .warn(...) / .error(...)."));
            }
            if (THREAD_SLEEP.matcher(line).find() || TIMEUNIT_SLEEP.matcher(line).find()) {
                findings.add(new Finding("FW_RULE_M_NO_SLEEP", file, lineNo,
                        "Thread.sleep is banned in generated tests",
                        "Use MobileWait or settle-aware action helpers."));
            }
        }
    }
}
