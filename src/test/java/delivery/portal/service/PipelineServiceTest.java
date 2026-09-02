package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.KeelPathCounts;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PipelineServiceTest {

    private static final String STORE = "./target/test-delivery-store-pipeline";
    private static final Long OWNER = 42L;

    private DeliveryPortalProperties props;
    private GeneratedWorkbookService workbooks;
    private FakeJobStarter jobStarter;
    private FakeJobStatus jobStatus;
    private PipelineService service;
    private ObjectMapper objectMapper;

    @BeforeMethod
    public void setUp() {
        props = new DeliveryPortalProperties();
        props.setStoreRoot(STORE);
        workbooks = new GeneratedWorkbookService(props);
        jobStarter = new FakeJobStarter();
        jobStatus = new FakeJobStatus();
        objectMapper = new ObjectMapper();
        service = new PipelineService(props, workbooks, jobStarter, jobStatus, objectMapper);
    }

    @Test
    public void start_runsConvertThenExecute_whenBothRunnable() throws Exception {
        seedWorkbook("proj-both", List.of(
                new ManualTestCase("TC_A", "Auto", "", "1. Click", "ok", "P1", "", "", "", "AUTOMATE"),
                new ManualTestCase("TC_E", "Exec", "", "1. Open", "ok", "P1", "", "", "", "EXECUTE")
        ));

        Map<String, Object> started = service.start("proj-both", OWNER, null);
        String pipelineId = String.valueOf(started.get("pipelineId"));
        Assert.assertEquals(started.get("status"), "RUNNING");

        service.tick(pipelineId);
        Assert.assertEquals(jobStarter.convertCalls.size(), 1);
        Assert.assertEquals(jobStarter.convertCalls.get(0).projectId(), "proj-both");
        Assert.assertTrue(jobStarter.convertCalls.get(0).useGenerated());

        jobStatus.complete(jobStarter.convertCalls.get(0).jobId());
        service.tick(pipelineId);

        Assert.assertEquals(jobStarter.executeCalls.size(), 1);
        Assert.assertTrue(jobStarter.executeCalls.get(0).useGenerated());

        jobStatus.complete(jobStarter.executeCalls.get(0).jobId());
        Map<String, Object> done = service.tick(pipelineId);

        Assert.assertEquals(done.get("status"), "COMPLETED");
        Map<String, Object> saved = readPipeline(pipelineId);
        Assert.assertEquals(stageStatus(saved, "AUTOMATE"), "COMPLETED");
        Assert.assertEquals(stageStatus(saved, "EXECUTE"), "COMPLETED");
    }

    @Test
    public void start_skipsAutomate_whenOnlyExecuteRunnable() throws Exception {
        seedWorkbook("proj-exec-only", List.of(
                new ManualTestCase("TC_E", "Exec", "", "1. Open", "ok", "P1", "", "", "", "EXECUTE")
        ));

        Map<String, Object> started = service.start("proj-exec-only", OWNER, null);
        String pipelineId = String.valueOf(started.get("pipelineId"));

        service.tick(pipelineId);
        Assert.assertTrue(jobStarter.convertCalls.isEmpty());
        Assert.assertEquals(jobStarter.executeCalls.size(), 1);

        jobStatus.complete(jobStarter.executeCalls.get(0).jobId());
        Map<String, Object> done = service.tick(pipelineId);

        Assert.assertEquals(done.get("status"), "COMPLETED");
        Map<String, Object> saved = readPipeline(pipelineId);
        Assert.assertEquals(stageStatus(saved, "AUTOMATE"), "SKIPPED");
        Assert.assertEquals(stageStatus(saved, "EXECUTE"), "COMPLETED");
    }

    @Test
    public void start_runsExecute_whenOnlyAutomateRunnable() throws Exception {
        // Execute surface includes AUTOMATE rows, so All-in-one still runs Execute after Automate.
        seedWorkbook("proj-auto-only", List.of(
                new ManualTestCase("TC_A", "Auto", "", "1. Click", "ok", "P1", "", "", "", "AUTOMATE")
        ));

        Map<String, Object> started = service.start("proj-auto-only", OWNER, null);
        String pipelineId = String.valueOf(started.get("pipelineId"));

        service.tick(pipelineId);
        Assert.assertEquals(jobStarter.convertCalls.size(), 1);

        jobStatus.complete(jobStarter.convertCalls.get(0).jobId());
        service.tick(pipelineId);
        Assert.assertEquals(jobStarter.executeCalls.size(), 1);

        jobStatus.complete(jobStarter.executeCalls.get(0).jobId());
        Map<String, Object> done = service.tick(pipelineId);

        Assert.assertEquals(done.get("status"), "COMPLETED");
        Map<String, Object> saved = readPipeline(pipelineId);
        Assert.assertEquals(stageStatus(saved, "AUTOMATE"), "COMPLETED");
        Assert.assertEquals(stageStatus(saved, "EXECUTE"), "COMPLETED");
    }

    @Test
    public void start_fails_whenNeitherSurfaceRunnable() throws Exception {
        seedWorkbook("proj-manual-only", List.of(
                new ManualTestCase("TC_M", "Manual", "", "1. Read", "ok", "P1", "", "", "", "MANUAL")
        ));

        try {
            service.start("proj-manual-only", OWNER, null);
            Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("SURFACE_MISMATCH")
                    || e.getMessage().contains("no runnable"));
        }
    }

    @Test
    public void start_generatesWorkbook_whenStoriesProvided() throws Exception {
        service = new PipelineService(
                props,
                workbooks,
                jobStarter,
                jobStatus,
                objectMapper,
                (projectId, ownerUserId, stories) -> {
                    workbooks.saveFromCases(projectId, List.of(
                            new ManualTestCase("TC_G", "Generated", "", "1. Go", "ok", "P1", "", "", "", "EXECUTE")
                    ), "GENERATE", "pipeline-test");
                }
        );

        Map<String, Object> started = service.start("proj-new", OWNER, "As a user I want login");
        String pipelineId = String.valueOf(started.get("pipelineId"));

        Map<String, Object> saved = readPipeline(pipelineId);
        Assert.assertEquals(stageStatus(saved, "GENERATE"), "COMPLETED");

        service.tick(pipelineId);
        Assert.assertTrue(jobStarter.convertCalls.isEmpty());
        Assert.assertEquals(jobStarter.executeCalls.size(), 1);
    }

    @Test
    public void tick_marksFailed_whenConvertJobFails() throws Exception {
        seedWorkbook("proj-fail-convert", List.of(
                new ManualTestCase("TC_A", "Auto", "", "1. Click", "ok", "P1", "", "", "", "AUTOMATE")
        ));

        Map<String, Object> started = service.start("proj-fail-convert", OWNER, null);
        String pipelineId = String.valueOf(started.get("pipelineId"));

        service.tick(pipelineId);
        String convertJobId = jobStarter.convertCalls.get(0).jobId();
        jobStatus.fail(convertJobId, "conversion blew up");

        Map<String, Object> failed = service.tick(pipelineId);
        Assert.assertEquals(failed.get("status"), "FAILED");
        Assert.assertTrue(String.valueOf(failed.get("message")).contains("conversion blew up"));
        Assert.assertTrue(jobStarter.executeCalls.isEmpty());
    }

    private void seedWorkbook(String projectId, List<ManualTestCase> cases) throws Exception {
        workbooks.saveFromCases(projectId, cases, "TEST", "pipeline-unit");
    }

    @SuppressWarnings("unchecked")
    private String stageStatus(Map<String, Object> pipeline, String stage) {
        Map<String, Object> stages = (Map<String, Object>) pipeline.get("stages");
        Map<String, Object> stageMap = (Map<String, Object>) stages.get(stage);
        return String.valueOf(stageMap.get("status"));
    }

    private Map<String, Object> readPipeline(String pipelineId) throws Exception {
        Path file = Path.of(STORE, "pipelines", pipelineId + ".json");
        Assert.assertTrue(Files.isRegularFile(file), "pipeline file should exist: " + file);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = objectMapper.readValue(file.toFile(), Map.class);
        return map;
    }

    static final class FakeJobStarter implements JobStarter {
        final List<ConvertCall> convertCalls = new ArrayList<>();
        final List<ExecuteCall> executeCalls = new ArrayList<>();
        private int seq;

        @Override
        public String startConvert(String projectId, Long ownerUserId, boolean useGenerated) {
            String jobId = "job_fake_" + (++seq);
            convertCalls.add(new ConvertCall(projectId, ownerUserId, useGenerated, jobId));
            return jobId;
        }

        @Override
        public String startExecute(String projectId, Long ownerUserId, boolean useGenerated) {
            String jobId = "exec_fake_" + (++seq);
            executeCalls.add(new ExecuteCall(projectId, ownerUserId, useGenerated, jobId));
            return jobId;
        }

        record ConvertCall(String projectId, Long ownerUserId, boolean useGenerated, String jobId) {}
        record ExecuteCall(String projectId, Long ownerUserId, boolean useGenerated, String jobId) {}
    }

    static final class FakeJobStatus implements JobStatusLookup {
        private final Map<String, JobRecord.Status> statuses = new ConcurrentHashMap<>();
        private final Map<String, String> errors = new ConcurrentHashMap<>();

        void complete(String jobId) {
            statuses.put(jobId, JobRecord.Status.COMPLETED);
        }

        void fail(String jobId, String message) {
            statuses.put(jobId, JobRecord.Status.FAILED);
            errors.put(jobId, message);
        }

        @Override
        public Optional<JobRecord.Status> status(String jobId) {
            return Optional.ofNullable(statuses.get(jobId));
        }

        @Override
        public Optional<String> errorMessage(String jobId) {
            return Optional.ofNullable(errors.get(jobId));
        }
    }
}
