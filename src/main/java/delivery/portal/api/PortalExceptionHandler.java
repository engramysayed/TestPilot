package delivery.portal.api;

import delivery.excel.InvalidExcelTemplateException;
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

    @ExceptionHandler(InvalidExcelTemplateException.class)
    public ResponseEntity<Map<String, String>> invalidExcel(InvalidExcelTemplateException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getErrorCode(), e.getMessage()).asMap());
    }

    @ExceptionHandler(ProjectArtifactService.BadPathException.class)
    public ResponseEntity<Map<String, String>> badArtifactPath(ProjectArtifactService.BadPathException e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_PATH", e.getMessage()).asMap());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflictState(IllegalStateException e) {
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
