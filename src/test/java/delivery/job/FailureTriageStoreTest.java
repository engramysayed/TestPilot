package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class FailureTriageStoreTest {

    @Test
    public void correctionKeepsSuggestionAndSurvivesReload() throws Exception {
        Path dir = Files.createTempDirectory("triage");
        FailureTriageStore store = new FailureTriageStore(dir.resolve("triage.json"));
        FailureClassifier.Classification written = store.put(
                "job_a",
                FailureClassifier.Kind.LOCATOR,
                FailureClassifier.Kind.ASSERTION);
        Assert.assertEquals(written.suggested(), FailureClassifier.Kind.LOCATOR);
        Assert.assertEquals(written.effective(), FailureClassifier.Kind.ASSERTION);
        Assert.assertTrue(written.userCorrected());

        FailureTriageStore reloaded = new FailureTriageStore(dir.resolve("triage.json"));
        FailureClassifier.Classification read = reloaded.get("job_a").orElseThrow();
        Assert.assertEquals(read.suggested(), FailureClassifier.Kind.LOCATOR);
        Assert.assertEquals(read.effective(), FailureClassifier.Kind.ASSERTION);
        Assert.assertTrue(read.userCorrected());
        Assert.assertTrue(reloaded.get("missing").isEmpty());
    }
}
