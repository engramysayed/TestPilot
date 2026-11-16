package delivery.agent.validation.contract;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class TestCaseContractValidatorTest {

    @Test
    public void validTc_withQuotedEntities_passesMandatoryRules() {
        ManualTestCase tc = new ManualTestCase(
                "TC_01",
                "Login happy path",
                "",
                "1. Open 'Login Page'\n2. Type in 'Username Field'",
                "User lands on 'Dashboard Page'",
                "P1",
                "",
                "",
                "",
                "",
                "");
        var report = TestCaseContractValidator.validate(
                List.of(tc), new TestCaseValidationContext("login dashboard", "https://example.com", "p1"));
        Assert.assertTrue(report.passed(), report.mandatoryMessages().toString());
    }

    @Test
    public void unquotedUiSteps_failEntityRule() {
        ManualTestCase tc = new ManualTestCase(
                "TC_02",
                "Bad quotes",
                "",
                "1. Open Login Page\n2. Click Login Button",
                "See dashboard",
                "P2",
                "",
                "",
                "",
                "",
                "");
        var report = TestCaseContractValidator.validate(List.of(tc), TestCaseValidationContext.empty());
        Assert.assertFalse(report.passed());
        Assert.assertTrue(report.findings().stream().anyMatch(f -> "TC_ENTITY_QUOTED_LABELS".equals(f.ruleId())));
    }

    @Test
    public void invalidTcId_surfacesStructuralFinding() {
        ManualTestCase tc = new ManualTestCase(
                "not-a-tc-id",
                "Title",
                "",
                "1. Open 'Login Page'",
                "Done",
                "",
                "",
                "",
                "",
                "",
                "");
        var report = TestCaseContractValidator.validate(List.of(tc), TestCaseValidationContext.empty());
        Assert.assertFalse(report.passed());
        Assert.assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().startsWith("TC_STRUCT_")));
    }
}