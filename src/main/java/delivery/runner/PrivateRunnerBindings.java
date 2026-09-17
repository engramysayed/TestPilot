package delivery.runner;

import delivery.identity.WorkspaceDirectory;
import delivery.portal.model.JobRecord;

import java.time.Instant;

public final class PrivateRunnerBindings {
    private PrivateRunnerBindings() {
    }

    public static PrivateRunnerRules.Enrollment toRules(WorkspaceDirectory.RunnerEnrollment row) {
        if (row == null) {
            return null;
        }
        Instant enrolled = row.enrolledAt() == null ? Instant.EPOCH : row.enrolledAt();
        return new PrivateRunnerRules.Enrollment(
                row.id(),
                row.tenant().value(),
                enrolled,
                row.revokedAt(),
                row.lastHeartbeat());
    }

    public static boolean sameTenant(WorkspaceDirectory.RunnerEnrollment runner, JobRecord job) {
        return runner != null && job != null && runner.tenant().value().equals(job.getTenantId());
    }
}
