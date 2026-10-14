package delivery.mobile.model;

/** Safe device inventory row for PortalStore / Frontend M1-07 (exact JSON names). */
public class MobileDeviceRecord {
    private String deviceId;
    private String tenantId;
    private String hostId;
    private String displayName;
    private String serialShort;
    private String model;
    private Integer apiLevel;
    private String abi;
    private String authorizedState;
    private String readiness;
    private String busy;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSerialShort() { return serialShort; }
    public void setSerialShort(String serialShort) { this.serialShort = serialShort; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getApiLevel() { return apiLevel; }
    public void setApiLevel(Integer apiLevel) { this.apiLevel = apiLevel; }
    public String getAbi() { return abi; }
    public void setAbi(String abi) { this.abi = abi; }
    public String getAuthorizedState() { return authorizedState; }
    public void setAuthorizedState(String authorizedState) { this.authorizedState = authorizedState; }
    public String getReadiness() { return readiness; }
    public void setReadiness(String readiness) { this.readiness = readiness; }
    public String getBusy() { return busy; }
    public void setBusy(String busy) { this.busy = busy; }
}
