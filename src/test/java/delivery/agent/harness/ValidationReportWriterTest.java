package delivery.agent.harness;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ValidationReportWriterTest {

    @Test
    public void persistFailOpen_bothFail_recordsEventAndStillReturnsFalse() throws Exception {
        Path project = Files.createTempDirectory("rep-fail");
        Files.createDirectories(project.resolve("agent-runs/job_fail/fp_task1"));
        Path fallbackBlock = project.resolve("agent-runs/job_fail/validation-fallback-fp_task1.json");
        Files.createDirectory(fallbackBlock);
        Path taskDirAsFile = Files.createTempFile("taskdir", ".file");

        AgentRunStore store = new AgentRunStore();
        Map<String, Object> report = ValidationReportWriter.baseReport("fp");
        report.put("validationStatus", "FAILED");

        boolean ok = ValidationReportWriter.persistFailOpen(
                project, "job_fail", taskDirAsFile, "fp_task1", report, store);
        Assert.assertFalse(ok, "advisory persistence failure must not throw");

        Path events = project.resolve(AgentRunStore.ROOT_DIR).resolve("job_fail/fp_task1/events.jsonl");
        Assert.assertTrue(Files.isRegularFile(events), "REPORT_PERSIST_FAILED should append when task dir exists");
        Assert.assertTrue(Files.readString(events).contains("REPORT_PERSIST_FAILED"));
    }

    @Test
    public void persistFailOpen_doesNotThrowArtifactIntegrity() throws Exception {
        Path project = Files.createTempDirectory("rep-no-int");
        Files.createDirectories(project.resolve("agent-runs"));
        Files.createFile(project.resolve("agent-runs/job2"));
        Path taskDirAsFile = Files.createTempFile("taskdir2", ".file");
        try {
            ValidationReportWriter.persistFailOpen(
                    project,
                    "job2",
                    taskDirAsFile,
                    "fp_task2",
                    ValidationReportWriter.baseReport("x"),
                    new AgentRunStore());
        } catch (delivery.job.ArtifactIntegrityException e) {
            Assert.fail("report persistence must not throw integrity exception");
        }
    }
}
