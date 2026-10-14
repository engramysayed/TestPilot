package delivery.mobile.permission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionPlan(
        boolean accepted,
        String failureCode,
        String message,
        Map<String, PermissionGrant> grants,
        boolean broadGrantAllRefused
) {
    public PermissionPlan {
        failureCode = failureCode == null ? "" : failureCode;
        message = message == null ? "" : message;
        grants = grants == null ? Map.of() : Map.copyOf(grants);
    }

    public static PermissionPlan ok(Map<String, PermissionGrant> grants) {
        return new PermissionPlan(true, "", "", grants, true);
    }

    public static PermissionPlan reject(String code, String message) {
        return new PermissionPlan(false, code, message, Map.of(), true);
    }
}
