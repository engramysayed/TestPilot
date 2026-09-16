package delivery.net;

import java.util.Map;

/**
 * Secrets a browser worker may hold: target credentials only. Control-plane
 * admin passwords and API keys stay off the job request.
 */
public record WorkerCredentialScope(String targetUsername, String targetPassword, String tenantId) {
    public WorkerCredentialScope {
        targetUsername = targetUsername == null ? "" : targetUsername;
        targetPassword = targetPassword == null ? "" : targetPassword;
        tenantId = tenantId == null ? "" : tenantId;
    }

    public static WorkerCredentialScope forBrowserJob(
            String targetUsername, String targetPassword, String tenantId, String adminPasswordIgnored
    ) {
        return new WorkerCredentialScope(targetUsername, targetPassword, tenantId);
    }

    public boolean containsControlPlaneSecret() {
        return false;
    }

    public Map<String, String> exported() {
        return Map.of(
                "targetUsername", targetUsername,
                "tenantId", tenantId
        );
    }
}
