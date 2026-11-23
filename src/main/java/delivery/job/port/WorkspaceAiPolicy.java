package delivery.job.port;

/**
 * Seam port for the per-workspace AI opt-in policy consulted by job runners.
 * Implemented by {@code delivery.portal.service.WorkspaceAiPolicyService}; the
 * interface lives here so {@code delivery.job.*} classes do not import from
 * {@code delivery.portal.*} (B05 ENGINE_NO_PORTAL — see JobContextSeamTest).
 */
public interface WorkspaceAiPolicy {
    /** True when AI validation AND failure-investigation are both enabled for the workspace. */
    boolean isFailureInvestigationEnabled(String projectId);

    /** True when AI validation AND framework-repair are both enabled for the workspace. */
    boolean isFrameworkRepairEnabled(String projectId);

    /** Idempotent-per-task debit for one AI task on the project owner. No-op on blank ids. */
    void chargeForAiTask(String projectId, String taskId);
}
