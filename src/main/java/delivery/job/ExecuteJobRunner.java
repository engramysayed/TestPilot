package delivery.job;

import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.store.ProjectStore;
import delivery.util.ProjectNaming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Prove-only execute job: Excel → ProvePhase → durable IR + evidence under execute-runs.
 */
public class ExecuteJobRunner {
    private final JobProgressTracker progress = new JobProgressTracker();

    public JobProgressTracker progress() {
        return progress;
    }

    public ExecuteJobResult run(ConversionJobRequest request, String jobId) throws Exception {
        return run(request, jobId, () -> false);
    }

    public ExecuteJobResult run(ConversionJobRequest request, String jobId, BooleanSupplier cancelCheck) throws Exception {
        List<ManualTestCase> cases = KeelPathCaseFilter.requireForSurface(
                new ExcelTcReader().read(request.excel()),
                KeelPathCaseFilter.Surface.EXECUTE,
                "No runnable test cases in workbook — only MANUAL rows are skipped on Execute"
        );

        String workFolder = ProjectNaming.fromBaseUrl(request.baseUrl(), Instant.now()) + "-exec";
        Path work = request.workDir().resolve(workFolder);
        Files.createDirectories(work);

        int proveUnits = cases.size();
        int jobTotal = proveUnits + 2; // + design compare + evidence save
        progress.update(0, jobTotal, "Execute starting");

        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl());
        Path dest = store.projectRoot(request.projectId()).resolve("execute-runs").resolve(jobId);
        Files.createDirectories(dest);

        ProvePhase prove = new ProvePhase(progress).withMirrorRoot(dest).withCancelCheck(cancelCheck);
        prove.prove(request, cases, null, work);

        progress.update(proveUnits, jobTotal, "Design compare");
        try {
            new DesignComparePhase().run(request, work, cases);

            progress.update(proveUnits + 1, jobTotal, "Saving evidence");
            copyTree(work.resolve("ir"), dest.resolve("ir"));
            copyTree(work.resolve("evidence"), dest.resolve("evidence"));
            progress.update(jobTotal, jobTotal, "Execute completed");
        } catch (Exception e) {
            // Persist whatever IR/evidence ProvePhase already wrote so the UI is not empty on fail.
            try {
                copyTree(work.resolve("ir"), dest.resolve("ir"));
                copyTree(work.resolve("evidence"), dest.resolve("evidence"));
            } catch (Exception ignored) {
                // best-effort
            }
            throw e;
        }

        return new ExecuteJobResult(progress.passed(), progress.todo(), "execute completed");
    }

    private static void copyTree(Path src, Path dest) throws Exception {
        if (!Files.exists(src)) {
            return;
        }
        Files.walk(src).forEach(path -> {
            try {
                Path rel = dest.resolve(src.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(rel);
                } else {
                    Files.createDirectories(rel.getParent());
                    Files.copy(path, rel, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
