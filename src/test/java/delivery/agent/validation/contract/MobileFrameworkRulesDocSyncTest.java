package delivery.agent.validation.contract;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mobile analogue of {@link FrameworkRulesDocSyncTest}: every FW_RULE_M_* code the doc mentions
 * must exist in the mobile validator source, and vice versa. Also smoke-tests that the validator
 * flags a crafted bad screen + bad test (so a future refactor can't accidentally silence it).
 */
public class MobileFrameworkRulesDocSyncTest {

    private static final Path DOC = Path.of("docs/MOBILE_FRAMEWORK_RULES.md");
    private static final Path VALIDATOR = Path.of(
            "src/main/java/delivery/agent/validation/contract/MobileFrameworkContractValidator.java");
    private static final Pattern RULE_CODE = Pattern.compile("\\bFW_RULE_M_[A-Z0-9_]+");

    @Test
    public void docAndValidatorShareRuleCodes() throws Exception {
        Set<String> docCodes = scan(Files.readString(DOC));
        Set<String> validatorCodes = scan(Files.readString(VALIDATOR));

        Set<String> inDocOnly = new TreeSet<>(docCodes);
        inDocOnly.removeAll(validatorCodes);
        Set<String> inValidatorOnly = new TreeSet<>(validatorCodes);
        inValidatorOnly.removeAll(docCodes);

        Assert.assertTrue(inDocOnly.isEmpty(),
                "FW_RULE_M_* in docs/MOBILE_FRAMEWORK_RULES.md but missing from MobileFrameworkContractValidator: "
                        + inDocOnly);
        Assert.assertTrue(inValidatorOnly.isEmpty(),
                "FW_RULE_M_* in MobileFrameworkContractValidator but missing from docs/MOBILE_FRAMEWORK_RULES.md: "
                        + inValidatorOnly);
    }

    @Test
    public void validatorFlagsCrossSectionOfRulesOnBadEmit() throws Exception {
        Path emitRoot = Files.createTempDirectory("mobile-validator-");
        try {
            Path screens = emitRoot.resolve("project/screens");
            Path tests = emitRoot.resolve("project/tests/generated");
            Files.createDirectories(screens);
            Files.createDirectories(tests);

            // Bad Screen: CSS, sleep, direct findElement, and a weird import.
            Files.writeString(screens.resolve("BadScreen.java"), String.join("\n", List.of(
                    "package project.screens;",
                    "import com.evil.pkg.Something;",
                    "import io.appium.java_client.android.AndroidDriver;",
                    "import org.openqa.selenium.By;",
                    "public class BadScreen {",
                    "  protected final AndroidDriver driver = null;",
                    "  protected final By bad = By.cssSelector(\"#x\");",
                    "  public void doIt() throws Exception {",
                    "    Thread.sleep(500);",
                    "    driver.findElement(bad).click();",
                    "    System.out.println(\"ouch\");",
                    "  }",
                    "}")), StandardCharsets.UTF_8);

            // Bad Test: no BaseMobileTest, declares a raw By, uses sleep.
            Files.writeString(tests.resolve("BadTest.java"), String.join("\n", List.of(
                    "package project.tests.generated;",
                    "import org.openqa.selenium.By;",
                    "import org.testng.annotations.Test;",
                    "public class BadTest {",
                    "  protected final By inline = By.id(\"fail\");",
                    "  @Test public void run() throws Exception { Thread.sleep(10); }",
                    "}")), StandardCharsets.UTF_8);

            List<MobileFrameworkContractValidator.Finding> findings =
                    MobileFrameworkContractValidator.validate(emitRoot);
            Set<String> codes = new TreeSet<>();
            for (var f : findings) codes.add(f.code);

            Assert.assertTrue(codes.contains("FW_RULE_M_NO_CSS"), codes.toString());
            Assert.assertTrue(codes.contains("FW_RULE_M_NO_SLEEP"), codes.toString());
            Assert.assertTrue(codes.contains("FW_RULE_M_SETTLE_DELEGATE"), codes.toString());
            Assert.assertTrue(codes.contains("FW_RULE_M_APPIUM_ONLY"), codes.toString());
            Assert.assertTrue(codes.contains("FW_RULE_M_NO_SYSOUT"), codes.toString());
            Assert.assertTrue(codes.contains("FW_RULE_M_TEST_BASE"), codes.toString());
            Assert.assertTrue(codes.contains("FW_RULE_M_TEST_NO_BY"), codes.toString());
        } finally {
            // best-effort cleanup
            try (var walk = Files.walk(emitRoot)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        }
    }

    private static Set<String> scan(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = RULE_CODE.matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }
}
