package delivery.job;

import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import delivery.excel.ManualTcExcelWriter;
import delivery.portal.service.GeneratedTcScopeFilter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Isolated fixture: invalid-login scope filtering and intent parse.
 * Does not read {@code delivery-store/} or call a live site/model.
 */
public class FacebookInvalidLoginConversionTest {

    private static final String INVALID_ONLY_STORY = """
            As a Facebook user I want clear feedback when I try to sign in with invalid credentials,
            so I know login failed. Wrong password must show an error and must not open the News Feed.
            """;

    @Test
    public void repairedWorkbook_parsesIntentsAndRunsDryConversion() throws Exception {
        List<ManualTestCase> rawCases = List.of(
                new ManualTestCase(
                        "TC_01",
                        "Invalid password login",
                        "No login required.",
                        "1. Open https://www.facebook.com/\n2. Enter in the Email or phone field\n3. Enter in the Password field\n4. Click Log in\n5. Confirm the message 'Wrong credentials' is visible",
                        "Error is shown",
                        "P1", "negative", "",
                        "wrong.user@example.com\nWrongPass123!\n\n",
                        "EXECUTE"),
                new ManualTestCase(
                        "TC_02",
                        "Login with valid credentials",
                        "",
                        "1. Open login\n2. Enter valid email\n3. Enter valid password\n4. Click Log in",
                        "News Feed opens",
                        "P1", "happy", "",
                        "ok.user@example.com\nGoodPass123!\n\n",
                        "EXECUTE")
        );
        List<ManualTestCase> cases = GeneratedTcScopeFilter.apply(INVALID_ONLY_STORY, rawCases);
        Assert.assertFalse(cases.stream().anyMatch(tc -> tc.title().toLowerCase().contains("valid credential")),
                "scope filter must drop out-of-scope happy-path login case");
        Assert.assertFalse(cases.isEmpty(), "invalid-login case must remain");

        for (ManualTestCase tc : cases) {
            var intents = StepIntentBinder.parseIntents(tc);
            Assert.assertFalse(intents.isEmpty(),
                    tc.tcId() + " must parse actionable intents after newline normalization");
        }

        Path work = Files.createTempDirectory("fb-invalid-login-isolated");
        Path excelPath = work.resolve("latest.xlsx");
        ManualTcExcelWriter.write(excelPath, cases);
        Path storeRoot = work.resolve("store");
        Files.createDirectories(storeRoot);
        Path jobWork = work.resolve("job");
        Files.createDirectories(jobWork);

        ConversionJobRequest request = new ConversionJobRequest(
                "prj_isolated_invalid_login",
                excelPath,
                "https://www.facebook.com/",
                "",
                "",
                jobWork,
                storeRoot,
                Path.of("customer-framework-template"),
                "NEW",
                "http://127.0.0.1:9",
                "dummy",
                false
        );

        ConversionJobResult result = new DryRunConversionService().run(request, new JobProgressTracker());
        Assert.assertEquals(result.todo(), cases.size());
        Assert.assertEquals(result.passed(), 0);
        Assert.assertTrue(result.todo() >= 1);
        Assert.assertFalse(result.message() != null
                        && result.message().contains("No actionable intents parsed from Excel steps"),
                "parser fix should eliminate blank-intent failures");
    }
}
