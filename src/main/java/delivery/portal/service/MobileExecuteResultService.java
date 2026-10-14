package delivery.portal.service;

import delivery.mobile.MobileProperties;
import delivery.mobile.result.MobileCaseResultView;
import delivery.mobile.result.MobileJobResultArtifactStore;
import delivery.mobile.result.MobileJobResultView;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Publish / load mobile Execute result views (M2-07).
 * Ingest stores the JSON artifact only — never mutates frozen admission inputs on the job or side files.
 */
@Service
public class MobileExecuteResultService {
    private final MobileProperties mobileProperties;
    private final PortalStore store;
    private final MobileJobResultArtifactStore artifactStore;

    public MobileExecuteResultService(MobileProperties mobileProperties, PortalStore store,
                                      MobileJobResultArtifactStore artifactStore) {
        this.mobileProperties = mobileProperties;
        this.store = store;
        this.artifactStore = artifactStore;
    }

    public boolean isEnabled() {
        return mobileProperties.isEnabled();
    }

    /**
     * Publish runner result without rewriting admission.
     * Captures admission side-file bytes + job inputSnapshotHash before write and verifies unchanged after.
     */
    public MobileJobResultView publish(String tenantId, String projectId, MobileJobResultView body) {
        requireEnabled();
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        String jobId = body.jobId() == null || body.jobId().isBlank()
                ? null
                : body.jobId().trim();
        if (jobId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobId required");
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job != null) {
            if (tenantId != null && !tenantId.equals(job.getTenantId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job");
            }
            if (projectId != null && job.getProjectId() != null && !projectId.equals(job.getProjectId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job");
            }
            tenantId = job.getTenantId();
            projectId = job.getProjectId();
        }
        if (tenantId == null || tenantId.isBlank() || projectId == null || projectId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant/project required");
        }

        String frozenInputHash = job == null ? null : job.getInputSnapshotHash();
        byte[] admissionBefore;
        try {
            admissionBefore = artifactStore.readAdmissionBytes(tenantId, projectId, jobId).orElse(null);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "admission read failed");
        }

        MobileJobResultView toStore = MobileJobResultView.of(
                jobId,
                body.attemptId(),
                projectId,
                body.runtime(),
                body.cases(),
                body.timingMs());

        try {
            artifactStore.write(tenantId, projectId, toStore);
        } catch (IllegalArgumentException e) {
            // e.g. raw serial refused by ObservedDeviceIdentity
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "persist failed");
        }

        // Assert admission inputs unchanged
        if (job != null) {
            JobRecord after = store.getJob(jobId).orElse(job);
            String afterHash = after.getInputSnapshotHash();
            if (frozenInputHash == null) {
                frozenInputHash = "";
            }
            if (afterHash == null) {
                afterHash = "";
            }
            if (!frozenInputHash.equals(afterHash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "FROZEN_INPUT_MUTATED");
            }
        }
        try {
            byte[] admissionAfter = artifactStore.readAdmissionBytes(tenantId, projectId, jobId).orElse(null);
            if (!Arrays.equals(admissionBefore, admissionAfter)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ADMISSION_SIDEFILE_MUTATED");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "admission verify failed");
        }
        return toStore;
    }

    public MobileJobResultView requireJobView(String projectId, String jobId, Long userId) {
        requireEnabled();
        ProjectRecord project = store.getOwnedProject(projectId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown project"));
        JobRecord job = store.getOwnedJob(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job"));
        if (job.getProjectId() != null && !projectId.equals(job.getProjectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job");
        }
        try {
            return artifactStore.read(project.getTenantId(), projectId, jobId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobile result not published"));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "read failed");
        }
    }

    public MobileCaseResultView requireCaseView(String projectId, String jobId, String caseId, Long userId) {
        MobileJobResultView job = requireJobView(projectId, jobId, userId);
        if (caseId == null || caseId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caseId required");
        }
        Optional<MobileCaseResultView> found = job.cases().stream()
                .filter(c -> caseId.equals(c.caseId()))
                .findFirst();
        return found.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown case"));
    }

    private void requireEnabled() {
        if (!mobileProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobile is disabled");
        }
    }
}
