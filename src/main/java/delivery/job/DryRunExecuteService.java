package delivery.job;

import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import delivery.portal.service.DesignReferenceService;
import delivery.store.ProjectStore;
import delivery.vision.DesignCompareEvidence;
import delivery.vision.DesignCompareResult;
import delivery.vision.VisionGroundingConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Execute dry-run: stub TODO IR under execute-runs without browser/AI.
 */
public class DryRunExecuteService {

    public ExecuteJobResult run(ConversionJobRequest request, String jobId, JobProgressTracker progress) throws Exception {
        return run(request, jobId, progress, () -> false);
    }

    public ExecuteJobResult run(
            ConversionJobRequest request,
            String jobId,
            JobProgressTracker progress,
            BooleanSupplier cancelCheck
    ) throws Exception {
        List<ManualTestCase> cases = KeelPathCaseFilter.requireForSurface(
                new ExcelTcReader().read(request.excel()),
                KeelPathCaseFilter.Surface.EXECUTE,
                "No runnable test cases in workbook — only MANUAL rows are skipped on Execute"
        );
        progress.update(0, cases.size(), "Dry-run execute (no local AI)");

        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl());
        Path projectRoot = store.projectRoot(request.projectId());
        Path dest = projectRoot.resolve("execute-runs").resolve(jobId);
        Files.createDirectories(dest);
        TcDraftStore drafts = new TcDraftStore(dest);
        DesignReferenceService references = new DesignReferenceService();
        String provider = VisionGroundingConfig.assertProviderId();
        String model = VisionGroundingConfig.assertModel();

        int i = 0;
        for (ManualTestCase tc : cases) {
            JobCancelSupport.checkCancelled(cancelCheck);
            i++;
            TcDraft draft = new TcDraft(
                    tc.tcId(),
                    tc.title(),
                    tc.steps(),
                    tc.expectedResult(),
                    TcDraftStatus.TODO,
                    List.of(),
                    List.of(),
                    false,
                    -1,
                    "",
                    "TODO: dry-run mode — enable local AI (set delivery.dry-run=false) to execute this case",
                    "",
                    0,
                    ""
            );
            drafts.write(draft);
            if (references.resolve(projectRoot, tc.tcId()).isPresent()) {
                Path evidenceDir = dest.resolve("evidence").resolve(tc.tcId());
                DesignCompareEvidence.write(
                        evidenceDir,
                        tc.tcId(),
                        DesignCompareResult.skipped("dry-run mode"),
                        null,
                        null,
                        provider,
                        model);
            }
            progress.recordOutcome(TcDraftStatus.TODO);
            progress.update(i, cases.size(),
                    "Dry-run TODO for " + tc.tcId()
                            + " — passed " + progress.passed()
                            + ", blocked " + progress.todo());
        }

        return new ExecuteJobResult(0, cases.size(), "dry-run execute completed");
    }
}
