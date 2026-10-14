package delivery.portal.engine;

import delivery.job.port.EngineSettings;
import delivery.job.port.JobContextPort;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import keel.contract.runner.WireJobKind;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * B05: the in-process {@link JobContextPort}. Default path in 0a; once B07 lands it is kept only as the
 * migration fallback ({@code keel.engine.in-process=true}).
 */
public final class InProcessJobContext implements JobContextPort {
    private final JobRecord job;
    private final EngineSettings settings;
    private final BooleanSupplier cancel;

    public InProcessJobContext(JobRecord job, EngineSettings settings, BooleanSupplier cancel) {
        this.job = job;
        this.settings = settings;
        this.cancel = cancel == null ? () -> false : cancel;
    }

    public static InProcessJobContext of(JobRecord job, DeliveryPortalProperties props, BooleanSupplier cancel) {
        return new InProcessJobContext(job, settings(props), cancel);
    }

    public static EngineSettings settings(DeliveryPortalProperties props) {
        return new EngineSettings(props.getLlmBaseUrl(), props.getLlmModel(), Path.of(props.getWorkDir()),
                Path.of(props.getStoreRoot()), Path.of(props.getTemplateRoot()));
    }

    public static WireJobKind wire(JobRecord.JobKind kind) {
        return WireJobKind.valueOf(kind.name());
    }

    @Override public String jobId() { return job.getJobId(); }
    @Override public String tenantId() { return job.getTenantId(); }
    @Override public Long ownerUserId() { return job.getOwnerUserId(); }
    @Override public String projectId() { return job.getProjectId(); }
    @Override public WireJobKind kind() { return wire(job.getJobKind()); }
    @Override public Path excelPath() { return job.getExcelPath(); }
    @Override public String baseUrl() { return job.getBaseUrl(); }
    @Override public String username() { return job.getUsername(); }
    @Override public String password() { return job.getPassword(); }
    @Override public String attemptId() { return job.getAttemptId(); }
    @Override public long cancelGeneration() { return job.getCancelGeneration(); }
    @Override public EngineSettings settings() { return settings; }
    @Override public boolean cancelRequested() { return cancel.getAsBoolean(); }
}
