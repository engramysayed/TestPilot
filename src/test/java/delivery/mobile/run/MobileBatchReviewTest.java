package delivery.mobile.run;

import delivery.excel.ManualTestCase;
import org.json.JSONObject;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MobileBatchReviewTest {

    @Test
    public void uploadedRowsAreWhatThePhoneWalks() {
        ManualTestCase uploaded = new ManualTestCase(
                "TC_UP", "Pay", "", "Tap the \"Pay\" button", "", "", "");
        ManualTestCase manual = new ManualTestCase(
                "TC_MAN", "Skip", "", "Tap \"No\"", "", "", "", "", "", "MANUAL");
        List<MobileRun.CaseInput> cases = MobileRunOrchestrator.casesFrom(List.of(uploaded, manual));
        assertEquals(cases.size(), 1);
        assertEquals(cases.get(0).tcId(), "TC_UP");
        assertEquals(cases.get(0).steps().get(0), "Tap the \"Pay\" button");
    }

    @Test
    public void reviewNamesTheFailedStepAndTheDesignStatus() throws Exception {
        MobileRun run = new MobileRun("mrun_review", "t", "p", 1L, "d",
                List.of(new MobileRun.CaseInput("TC1", "Pay", List.of("Tap Pay"), List.of(), List.of())),
                "com.app", ".Main");
        run.results.add(new JSONObject()
                .put("tcId", "TC1")
                .put("outcome", "FAILED")
                .put("failedStep", 2)
                .put("detail", "element not found"));
        Path evidence = Files.createTempDirectory("batch-review");
        Path tc = evidence.resolve("TC1");
        Files.createDirectories(tc);
        Files.writeString(tc.resolve("design-compare.json"), "{\"status\":\"MISMATCH\"}");
        String text = MobileBatchReview.of(run, evidence);
        assertTrue(text.contains("After this batch: 0 passed, 1 failed."), text);
        assertTrue(text.contains("TC1 failed at step 2"), text);
        assertTrue(text.contains("TC1 MISMATCH"), text);
    }

    @Test
    public void modelNoteKeepsTheWrittenSentenceWhenTheModelIsBlank() {
        String written = "After this batch: 1 passed, 0 failed.";
        assertEquals(written, MobileBatchModelNote.join(written, "  \n"));
        assertEquals(written + " Model: The pay step was the only failure.",
                MobileBatchModelNote.join(written, "The pay step was the only failure.\nMore"));
    }
}
