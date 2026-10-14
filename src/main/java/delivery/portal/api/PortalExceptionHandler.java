package delivery.portal.api;

import delivery.excel.InvalidExcelTemplateException;
import delivery.portal.roles.RoleRequiredException;
import delivery.portal.service.ProjectArtifactService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "delivery.portal.api")
public class PortalExceptionHandler {
    private static final Logger log = LogManager.getLogger(PortalExceptionHandler.class);


    @ExceptionHandler(delivery.portal.service.credits.InsufficientCreditsException.class)
    public ResponseEntity<Map<String, Object>> insufficientCredits(
            delivery.portal.service.credits.InsufficientCreditsException e) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("code", delivery.portal.service.credits.InsufficientCreditsException.CODE);
        body.put("message", e.getMessage());
        body.put("required", e.required());
        body.put("balance", e.balance());
        body.put("jobKind", e.jobKind());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(body);
    }

    @ExceptionHandler(delivery.portal.service.credits.AccountApprovalException.class)
    public ResponseEntity<Map<String, Object>> accountNotApproved(
            delivery.portal.service.credits.AccountApprovalException e) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("code", e.code());
        body.put("message", e.getMessage());
        body.put("approvalStatus", e.approvalStatus());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(delivery.job.JobAdmissionException.class)
    public ResponseEntity<Map<String, String>> admissionRejected(delivery.job.JobAdmissionException e) {
        HttpStatus status = "BUDGET_UNAVAILABLE".equals(e.code()) ? HttpStatus.SERVICE_UNAVAILABLE
                : "ENVIRONMENT_INVALID".equals(e.code()) ? HttpStatus.CONFLICT : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status)
                .body(new ApiError(e.code(), e.getMessage()).asMap());
    }

    @ExceptionHandler(delivery.store.StaleLibraryRevisionException.class)
    public ResponseEntity<Map<String, String>> staleLibrary(delivery.store.StaleLibraryRevisionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("LIBRARY_CONFLICT", e.getMessage()).asMap());
    }

    @ExceptionHandler(InvalidExcelTemplateException.class)
    public ResponseEntity<Map<String, String>> invalidExcel(InvalidExcelTemplateException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getErrorCode(), e.getMessage()).asMap());
    }

    @ExceptionHandler(ProjectArtifactService.BadPathException.class)
    public ResponseEntity<Map<String, String>> badArtifactPath(ProjectArtifactService.BadPathException e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_PATH", e.getMessage()).asMap());
    }


    @ExceptionHandler(RoleRequiredException.class)
    public ResponseEntity<Map<String, String>> roleRequired(RoleRequiredException e) {
        boolean missingRoleIdOnApprove = e.getMessage() != null
                && e.getMessage().toLowerCase().contains("roleid is required");
        HttpStatus status = missingRoleIdOnApprove ? HttpStatus.BAD_REQUEST : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(new ApiError(e.errorCode(), e.getMessage() == null ? "Not allowed" : e.getMessage()).asMap());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ROLE_REQUIRED", e.getMessage() == null ? "Not allowed" : e.getMessage()).asMap());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflictState(IllegalStateException e) {
        if ("LEASE_LOST".equals(e.getMessage())) return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("LEASE_LOST", "lease expired or attempt no longer owned").asMap());
        String code = e.getMessage() != null && e.getMessage().contains("UPDATE")
                ? "UPDATE_WITHOUT_FRAMEWORK"
                : "CONFLICT";
        return ResponseEntity.badRequest().body(new ApiError(code, e.getMessage()).asMap());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", e.getResourcePath() == null ? "Not found" : e.getResourcePath()).asMap());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Unhandled portal error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Unexpected server error").asMap());
    }
}
