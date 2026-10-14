package delivery.mobile.apk;

/**
 * Inputs for inspecting a quarantined APK against optional app/device policy.
 */
public final class ApkInspectionRequest {
    private String expectedPackage;
    private ApkRevisionPolicy.PriorRevision priorRevision;
    private ApkRevisionPolicy.DeviceConstraints deviceConstraints;
    private String selectedLaunchActivity;
    private String selectedWaitActivity;
    private boolean allowDowngrade;
    private boolean allowSignatureReplacement;

    public String getExpectedPackage() { return expectedPackage; }
    public void setExpectedPackage(String expectedPackage) { this.expectedPackage = expectedPackage; }
    public ApkRevisionPolicy.PriorRevision getPriorRevision() { return priorRevision; }
    public void setPriorRevision(ApkRevisionPolicy.PriorRevision priorRevision) {
        this.priorRevision = priorRevision;
    }
    public ApkRevisionPolicy.DeviceConstraints getDeviceConstraints() { return deviceConstraints; }
    public void setDeviceConstraints(ApkRevisionPolicy.DeviceConstraints deviceConstraints) {
        this.deviceConstraints = deviceConstraints;
    }
    public String getSelectedLaunchActivity() { return selectedLaunchActivity; }
    public void setSelectedLaunchActivity(String selectedLaunchActivity) {
        this.selectedLaunchActivity = selectedLaunchActivity;
    }
    public String getSelectedWaitActivity() { return selectedWaitActivity; }
    public void setSelectedWaitActivity(String selectedWaitActivity) {
        this.selectedWaitActivity = selectedWaitActivity;
    }
    public boolean isAllowDowngrade() { return allowDowngrade; }
    public void setAllowDowngrade(boolean allowDowngrade) { this.allowDowngrade = allowDowngrade; }
    public boolean isAllowSignatureReplacement() { return allowSignatureReplacement; }
    public void setAllowSignatureReplacement(boolean allowSignatureReplacement) {
        this.allowSignatureReplacement = allowSignatureReplacement;
    }
}
