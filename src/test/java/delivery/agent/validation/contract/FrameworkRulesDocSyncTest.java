package delivery.agent.validation.contract;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Smoke test: every FW_RULE_* code the doc mentions must exist in the validator source, and vice
 * versa. Catches drift when a rule is added to one side but not the other.
 */
public class FrameworkRulesDocSyncTest {

    private static final Path DOC = Path.of("docs/FRAMEWORK_RULES.md");
    private static final Path VALIDATOR = Path.of(
            "src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java");

    private static final Pattern RULE_CODE = Pattern.compile("\\bFW_RULE_[A-Z0-9_]+");

    @Test
    public void docAndValidatorShareRuleCodes() throws Exception {
        Set<String> docCodes = scan(Files.readString(DOC));
        Set<String> validatorCodes = scan(Files.readString(VALIDATOR));

        Set<String> inDocOnly = new TreeSet<>(docCodes);
        inDocOnly.removeAll(validatorCodes);
        Set<String> inValidatorOnly = new TreeSet<>(validatorCodes);
        inValidatorOnly.removeAll(docCodes);

        Assert.assertTrue(
                inDocOnly.isEmpty(),
                "FW_RULE_* codes in docs/FRAMEWORK_RULES.md but missing from FrameworkContractValidator.java: "
                        + inDocOnly);
        Assert.assertTrue(
                inValidatorOnly.isEmpty(),
                "FW_RULE_* codes in FrameworkContractValidator.java but missing from docs/FRAMEWORK_RULES.md: "
                        + inValidatorOnly);
    }

    private static Set<String> scan(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = RULE_CODE.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }
}
