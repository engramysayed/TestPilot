package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.KeelPathCounts;
import delivery.excel.KeelPathSurfaceGuard;
import delivery.excel.ManualTestCase;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.worker.ConversionWorker;
import delivery.portal.worker.ExecuteWorker;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PortalJobStarter implements JobStarter {

    private final PortalStore store;
    private final ConversionWorker convertWorker;
    private final ExecuteWorker executeWorker;
    private final GeneratedWorkbookService workbooks;

    public PortalJobStarter(
            PortalStore store,
            ConversionWorker convertWorker,
            ExecuteWorker executeWorker,
            GeneratedWorkbookService workbooks
    ) {
        this.store = store;
        this.convertWorker = convertWorker;
        this.executeWorker = executeWorker;
        this.workbooks = workbooks;
    }

    @Override
    public String startConvert(String projectId, Long ownerUserId, boolean useGenerated) {
        return startJob(projectId, ownerUserId, useGenerated, JobRecord.JobKind.CONVERT, "job_");
    }

    @Override
    public String startExecute(String projectId, Long ownerUserId, boolean useGenerated) {
        return startJob(projectId, ownerUserId, useGenerated, JobRecord.JobKind.EXECUTE, "exec_");
    }

    private String startJob(
            String projectId,
            Long ownerUserId,
            boolean useGenerated,
            JobRecord.JobKind kind,
            String idPrefix
    ) {
        ProjectRecord project = store.getOwnedProject(projectId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown project"));
        if (project.isArchived()) {
            throw new IllegalStateException("PROJECT_ARCHIVED");
        }
        String baseUrl = project.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("MISSING_BASE_URL");
        }

        Path excelPath;
        try {
            if (!useGenerated) {
                throw new IllegalArgumentException("Pipeline requires useGenerated workbook");
            }
            excelPath = workbooks.copyForJob(projectId);
        } catch (Exception e) {
            throw new IllegalStateException("NO_GENERATED_WORKBOOK", e);
        }

        KeelPathCaseFilter.Surface surface = kind == JobRecord.JobKind.CONVERT
                ? KeelPathCaseFilter.Surface.AUTOMATE
                : KeelPathCaseFilter.Surface.EXECUTE;
        try {
            List<ManualTestCase> cases = new ExcelTcReader(true).read(excelPath);
            Optional<String> block = KeelPathSurfaceGuard.hardBlock(surface, KeelPathCounts.from(cases));
            if (block.isPresent()) {
                throw new IllegalStateException("SURFACE_MISMATCH: " + block.get());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("INVALID_EXCEL", e);
        }

        String jobId = idPrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String mode = kind == JobRecord.JobKind.CONVERT ? "NEW" : "EXECUTE";
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                ownerUserId,
                mode,
                excelPath,
                baseUrl,
                "",
                "",
                false,
                kind
        );
        store.saveJob(job);
        if (kind == JobRecord.JobKind.CONVERT) {
            convertWorker.submit(jobId);
        } else {
            executeWorker.submit(jobId);
        }
        return jobId;
    }
}
