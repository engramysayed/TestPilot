package delivery.job;

import delivery.codegen.CodeWriter;
import delivery.codegen.DomainCatalogWriter;
import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraftStatus;
import delivery.packager.FrameworkPackager;
import delivery.store.ProjectStore;
import delivery.store.TcDiffService;
import delivery.util.ProjectNaming;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Packages a customer ZIP without browser/AI — every TC becomes TODO.
 * Used until local Ollama is available (delivery.dry-run=true).
 */
public class DryRunConversionService {

    public ConversionJobResult run(ConversionJobRequest request, JobProgressTracker progress) throws Exception {
        return run(request, progress, () -> false);
    }

    public ConversionJobResult run(
            ConversionJobRequest request,
            JobProgressTracker progress,
            BooleanSupplier cancelCheck
    ) throws Exception {
        String mode = request.mode() == null ? "NEW" : request.mode().trim().toUpperCase();
        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl());
        if ("UPDATE".equals(mode) && !store.hasFramework(request.projectId())) {
            throw new IllegalStateException("UPDATE_WITHOUT_FRAMEWORK");
        }

        List<ManualTestCase> cases = KeelPathCaseFilter.requireForSurface(
                new ExcelTcReader().read(request.excel()),
                KeelPathCaseFilter.Surface.AUTOMATE,
                "No AUTOMATE test cases in workbook — use Generate KeelPath=AUTOMATE, or leave KeelPath blank for legacy sheets"
        );
        progress.update(0, cases.size(), "Dry-run packaging (no local AI)");

        Path work = request.workDir().resolve(ProjectNaming.fromBaseUrl(request.baseUrl(), Instant.now()) + "-dry");
        Path projectDir = work.resolve("project");
        Files.createDirectories(projectDir);
        FrameworkPackager packager = new FrameworkPackager();
        if ("UPDATE".equals(mode) && store.hasFramework(request.projectId())) {
            packager.copyTemplate(store.projectRoot(request.projectId()).resolve("framework"), projectDir);
        } else {
            packager.copyTemplate(request.templateRoot(), projectDir);
        }

        List<TcOutcome> outcomes = new ArrayList<>();
        int i = 0;
        for (ManualTestCase tc : cases) {
            JobCancelSupport.checkCancelled(cancelCheck);
            i++;
            outcomes.add(new TcOutcome(
                    tc.tcId(),
                    TcStatus.TODO,
                    List.of(),
                    "TODO: dry-run mode — enable local AI (set delivery.dry-run=false) to author this case",
                    null
            ));
            progress.recordOutcome(TcDraftStatus.TODO);
            progress.update(i, cases.size(),
                    "Dry-run TODO for " + tc.tcId()
                            + " — passed " + progress.passed()
                            + ", blocked " + progress.todo());
        }

        new CodeWriter(request.templateRoot().resolve("templates")).write(projectDir, outcomes);
        DomainCatalogWriter.write(projectDir, outcomes);
        packager.writeScoreReport(projectDir, outcomes);
        packager.writeTargetConfig(projectDir, request.baseUrl(), request.username(), request.password());
        Path zip = work.resolve("package.zip");
        packager.zip(projectDir, zip);

        JSONObject hashes = new JSONObject(new TcDiffService().hashesOf(cases));
        Path hashFile = store.projectRoot(request.projectId()).resolve("tc-hashes.json");
        Files.createDirectories(hashFile.getParent());
        Files.writeString(hashFile, hashes.toString(2));
        store.saveVersion(request.projectId(), projectDir, zip);

        JSONObject lastJob = new JSONObject();
        lastJob.put("status", "COMPLETED");
        lastJob.put("passedCount", 0);
        lastJob.put("todoCount", outcomes.size());
        lastJob.put("mode", mode);
        lastJob.put("dryRun", true);
        lastJob.put("completedAt", java.time.Instant.now().toString());
        store.writeLastJob(request.projectId(), lastJob);

        return new ConversionJobResult(zip, 0, outcomes.size(),
                projectDir.resolve("docs/AUTOMATION_SCORE.md"),
                "dry-run completed");
    }
}
