package delivery.job;

import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.portal.service.GeneratedTcScopeFilter;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Re-runs Automate on the Facebook invalid-login generated workbook after parser/scope fixes.
 */
public class FacebookInvalidLoginConversionTest {

    private static final String PROJECT_ID = "prj_869144db77b8";
    private static final String INVALID_ONLY_STORY = """
            As a Facebook user I want clear feedback when I try to sign in with invalid credentials,
            so I know login failed. Wrong password must show an error and must not open the News Feed.
            """;

    @Test
    public void repairedWorkbook_parsesIntentsAndRunsConversion() throws Exception {
        Path repo = Path.of(".").toAbsolutePath().normalize();
        Path rawCsv = repo.resolve("delivery-store").resolve(PROJECT_ID).resolve("generated").resolve("latest.csv");
        Assert.assertTrue(Files.isRegularFile(rawCsv), "missing generated latest.csv — run Generate first");

        String raw = Files.readString(rawCsv, StandardCharsets.UTF_8);
        List<ManualTestCase> cases = GeneratedTcScopeFilter.apply(
                INVALID_ONLY_STORY, GeneratedTcCsvParser.parse(raw));
        Assert.assertFalse(cases.stream().anyMatch(tc -> tc.title().toLowerCase().contains("valid credential")),
                "scope filter must drop out-of-scope happy-path login case");

        for (ManualTestCase tc : cases) {
            var intents = delivery.authoring.StepIntentBinder.parseIntents(tc);
            Assert.assertFalse(intents.isEmpty(),
                    tc.tcId() + " must parse actionable intents after newline normalization");
        }

        Path generatedDir = rawCsv.getParent();
        Path excelPath = generatedDir.resolve("latest.xlsx");
        ManualTcExcelWriter.write(excelPath, cases);
        Files.writeString(generatedDir.resolve("latest.csv"), GeneratedTcCsvParser.toCsv(cases), StandardCharsets.UTF_8);

        Path workDir = repo.resolve("target").resolve("fb-invalid-login-work");
        Files.createDirectories(workDir);
        Path jobWork = workDir.resolve("facebook-com-" + System.currentTimeMillis());
        ConversionJobRequest request = new ConversionJobRequest(
                PROJECT_ID,
                excelPath,
                "https://www.facebook.com/",
                "",
                "",
                jobWork,
                repo.resolve("delivery-store"),
                repo.resolve("customer-framework-template"),
                "NEW",
                "http://127.0.0.1:11434",
                "gemma4:e2b",
                false
        );

        ConversionJobResult result = new ConversionJobRunner().run(request);
        Path score = result.scoreReport();
        Assert.assertTrue(Files.isRegularFile(score), "AUTOMATION_SCORE.md missing");
        String scoreText = Files.readString(score, StandardCharsets.UTF_8);
        System.out.println("=== AUTOMATION SCORE ===");
        System.out.println(scoreText);

        Assert.assertFalse(scoreText.contains("No actionable intents parsed from Excel steps"),
                "parser fix should eliminate blank-intent failures");
        Assert.assertTrue(result.passed() + result.todo() >= cases.size(),
                "expected one outcome row per TC");
    }
}
