package delivery.mobile.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of POST mobile APK upload inspection (M1-02).
 * status READY → revision frozen; ACTIVITY_SELECTION_REQUIRED → 422 detail for Frontend.
 */
public class MobileApkUploadResult {
    private String status;
    private MobileAppRevisionRecord revision;
    private List<String> launcherCandidates = new ArrayList<>();
    private String errorCode;
    private String message;
    private String sha256;
    private String uploadId;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public MobileAppRevisionRecord getRevision() { return revision; }
    public void setRevision(MobileAppRevisionRecord revision) { this.revision = revision; }
    public List<String> getLauncherCandidates() { return launcherCandidates; }
    public void setLauncherCandidates(List<String> launcherCandidates) {
        this.launcherCandidates = launcherCandidates == null
                ? new ArrayList<>() : new ArrayList<>(launcherCandidates);
    }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
}
