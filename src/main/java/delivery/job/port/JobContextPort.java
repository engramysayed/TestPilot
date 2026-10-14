package delivery.job.port;

import keel.contract.runner.WireJobKind;
import keel.contract.runner.WireStage;

import java.nio.file.Path;

/**
 * B05 seam: everything a worker may know about "its" job, without seeing PortalStore or JobRecord.
 * In 0a the only implementation is {@code delivery.portal.engine.InProcessJobContext}; B07 adds the
 * v2-API-backed one in keel-engine.
 */
public interface JobContextPort {
    String jobId();

    String tenantId();

    Long ownerUserId();

    String projectId();

    WireJobKind kind();

    Path excelPath();

    String baseUrl();

    String username();

    String password();

    String attemptId();

    long cancelGeneration();

    EngineSettings settings();

    boolean cancelRequested();

    /** Best-effort; in-process this is the DurableJobClaim stage marker. */
    default void stage(WireStage stage) {
    }

    default void progress(String message) {
    }
}
