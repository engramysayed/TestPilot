package delivery.job;

import delivery.excel.GeneratedTcCsvParser;
import delivery.excel.ManualTestCase;
import delivery.excel.UserStoryBulkParser;
import delivery.portal.service.TcGenerateService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class GenerateBatchJobRunner {
    private final JobProgressTracker progress = new JobProgressTracker();

    public JobProgressTracker progress() {
        return progress;
    }

    public GenerateBatchJobResult run(GenerateBatchJobRequest request, TcGenerateService generate) throws Exception {
        return run(request, generate, () -> false);
    }

    public GenerateBatchJobResult run(
            GenerateBatchJobRequest request,
            TcGenerateService generate,
            BooleanSupplier cancelCheck
    ) throws Exception {
        String raw = Files.readString(request.storiesPath(), StandardCharsets.UTF_8);
        List<UserStoryBulkParser.UserStoryEntry> stories = UserStoryBulkParser.parse(raw);
        progress.update(0, stories.size(), "Queued " + stories.size() + " user stories");

        List<ManualTestCase> allCases = new ArrayList<>();
        int failedStories = 0;
        String lastStoryFailure = null;
        int index = 0;
        for (UserStoryBulkParser.UserStoryEntry entry : stories) {
            JobCancelSupport.checkCancelled(cancelCheck);
            index++;
            progress.update(index, stories.size(),
                    "Generating " + entry.usId() + " — " + shortLabel(entry.title()));
            try {
                List<ManualTestCase> batch = generate.generateCasesForStory(
                        request.project(),
                        entry.toPromptBlock(),
                        request.reviewPass(),
                        entry.usId(),
                        request.model(),
                        cancelCheck
                );
                allCases.addAll(batch);
                progress.setScores(allCases.size(), failedStories);
            } catch (JobCancelledException e) {
                throw e;
            } catch (Exception e) {
                failedStories++;
                lastStoryFailure = entry.usId() + ": "
                        + (e.getMessage() == null ? "error" : e.getMessage());
                progress.setScores(allCases.size(), failedStories);
                progress.update(index, stories.size(), "Failed " + lastStoryFailure);
                if (request.failFast()) {
                    throw e;
                }
            }
        }

        if (allCases.isEmpty()) {
            String detail = lastStoryFailure != null ? lastStoryFailure : "none recorded";
            throw new IllegalStateException(
                    "No test cases were generated from bulk stories — last failure: " + detail);
        }

        Files.createDirectories(request.outputDir());
        Path outputCsv = request.outputDir().resolve("generated-tcs.csv");
        Files.writeString(outputCsv, GeneratedTcCsvParser.toCsv(allCases), StandardCharsets.UTF_8);

        String message = "Generated " + allCases.size() + " test cases from "
                + stories.size() + " user stories";
        if (failedStories > 0) {
            message += " (" + failedStories + " stories failed)";
        }
        progress.setScores(allCases.size(), failedStories);
        progress.update(stories.size(), stories.size(), message);
        return new GenerateBatchJobResult(allCases.size(), failedStories, outputCsv, message);
    }

    private static String shortLabel(String title) {
        if (title == null || title.isBlank()) {
            return "story";
        }
        String t = title.trim();
        return t.length() > 48 ? t.substring(0, 45) + "…" : t;
    }
}
