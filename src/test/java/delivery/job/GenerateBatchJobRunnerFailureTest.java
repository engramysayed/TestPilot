package delivery.job;

import delivery.excel.ManualTestCase;
import delivery.portal.model.ProjectRecord;
import delivery.portal.service.TcGenerateService;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

public class GenerateBatchJobRunnerFailureTest {

    @Test
    public void allStoriesFail_throwsWithLastStoryError() throws Exception {
        Path storiesPath = Files.createTempFile("bulk-stories-fail", ".txt");
        Files.writeString(storiesPath,
                "US_ID,Title,Story\n"
                        + "US-1,Facebook Login,As a user I want secure login.\n",
                StandardCharsets.UTF_8);
        Path outputDir = Files.createTempDirectory("gen-batch-fail");

        TcGenerateService generate = new TcGenerateService(null, null, null, null, null) {
            @Override
            public List<ManualTestCase> generateCasesForStory(
                    ProjectRecord project,
                    String stories,
                    boolean reviewPass,
                    String storyLabel,
                    String model,
                    BooleanSupplier cancelCheck
            ) {
                throw new IllegalArgumentException("QUALITY_GATE: tcId 'TC_01': vague assert");
            }
        };

        GenerateBatchJobRequest request = new GenerateBatchJobRequest(
                new ProjectRecord("prj_x", "Facebook Login Negative", 1L, 0),
                storiesPath,
                outputDir,
                false,
                false,
                "test-model"
        );

        GenerateBatchJobRunner runner = new GenerateBatchJobRunner();
        try {
            runner.run(request, generate);
            Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            Assert.assertNotNull(msg);
            Assert.assertTrue(msg.contains("QUALITY_GATE"), msg);
            Assert.assertTrue(msg.contains("vague assert"), msg);
        }
    }
}
