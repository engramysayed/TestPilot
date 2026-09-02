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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class JobCancelSupportTest {

    @Test
    public void cancelFlagStopsIterationOnNextCase() {
        List<ManualTestCase> cases = List.of(
                tc("TC-1"),
                tc("TC-2"),
                tc("TC-3")
        );
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger processed = new AtomicInteger();

        try {
            for (ManualTestCase tc : cases) {
                JobCancelSupport.checkCancelled(cancelled::get);
                processed.incrementAndGet();
                if ("TC-2".equals(tc.tcId())) {
                    cancelled.set(true);
                }
            }
            Assert.fail("expected JobCancelledException");
        } catch (JobCancelledException e) {
            Assert.assertEquals(processed.get(), 2);
        }
    }

    @Test
    public void generateBatchStopsBetweenStoriesWhenCancelled() throws Exception {
        Path storiesPath = Files.createTempFile("bulk-stories", ".txt");
        Files.writeString(storiesPath,
                "US_ID,Title,Story\n"
                        + "US-1,First story,Story one\n"
                        + "US-2,Second story,Story two\n"
                        + "US-3,Third story,Story three\n",
                StandardCharsets.UTF_8);
        Path outputDir = Files.createTempDirectory("gen-batch-cancel");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger storyCalls = new AtomicInteger();

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
                storyCalls.incrementAndGet();
                if ("US-2".equals(storyLabel)) {
                    cancelled.set(true);
                }
                return List.of(tc(storyLabel + "-TC"));
            }
        };

        GenerateBatchJobRequest request = new GenerateBatchJobRequest(
                new ProjectRecord("prj_x", "Test", 1L, 0),
                storiesPath,
                outputDir,
                false,
                false,
                "test-model"
        );

        GenerateBatchJobRunner runner = new GenerateBatchJobRunner();
        try {
            runner.run(request, generate, cancelled::get);
            Assert.fail("expected JobCancelledException");
        } catch (JobCancelledException e) {
            Assert.assertEquals(storyCalls.get(), 2);
        }
    }

    @Test
    public void generateBatchRethrowsCancelDuringStory() throws Exception {
        Path storiesPath = Files.createTempFile("bulk-stories-mid-cancel", ".txt");
        Files.writeString(storiesPath,
                "US_ID,Title,Story\n"
                        + "US-1,Only story,Story one\n",
                StandardCharsets.UTF_8);
        Path outputDir = Files.createTempDirectory("gen-batch-mid-cancel");

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
                throw new JobCancelledException();
            }
        };

        GenerateBatchJobRequest request = new GenerateBatchJobRequest(
                new ProjectRecord("prj_x", "Test", 1L, 0),
                storiesPath,
                outputDir,
                false,
                false,
                "test-model"
        );

        try {
            new GenerateBatchJobRunner().run(request, generate, () -> true);
            Assert.fail("expected JobCancelledException");
        } catch (JobCancelledException e) {
            Assert.assertEquals(e.getMessage(), "Job cancelled");
        }
    }

    @Test
    public void awaitOrCancel_returnsWhenFutureCompletes() throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = ex.submit(() -> "ok");
            Assert.assertEquals(JobCancelSupport.awaitOrCancel(future, 5_000, () -> false), "ok");
        } finally {
            ex.shutdownNow();
        }
    }

    @Test
    public void awaitOrCancel_stopsSoonAfterCancelFlag() throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = ex.submit(() -> {
                Thread.sleep(30_000);
                return "late";
            });
            AtomicBoolean cancelled = new AtomicBoolean(false);
            Thread canceller = new Thread(() -> {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cancelled.set(true);
            });
            canceller.start();
            long start = System.currentTimeMillis();
            try {
                JobCancelSupport.awaitOrCancel(future, 30_000, cancelled::get);
                Assert.fail("expected JobCancelledException");
            } catch (JobCancelledException e) {
                Assert.assertTrue(System.currentTimeMillis() - start < 2_000,
                        "cancel should not wait for the Ollama timeout");
            }
        } finally {
            ex.shutdownNow();
        }
    }

    private static ManualTestCase tc(String id) {
        return new ManualTestCase(id, "title", "", "step", "expected", "P1", "", "", "");
    }
}
