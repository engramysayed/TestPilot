package delivery.mobile.appium;

/**
 * Bookkeeping handle for a per-job Appium child process. Only this PID (tree) may be stopped.
 */
public final class OwnedAppiumProcess {
    private final String processRef;
    private final long pid;
    private final String ownerKey;
    private final int serverPort;
    private final int systemPort;
    private final int chromedriverPort;
    private final String baseUrl;

    public OwnedAppiumProcess(String processRef,
                              long pid,
                              String ownerKey,
                              int serverPort,
                              int systemPort,
                              int chromedriverPort,
                              String baseUrl) {
        this.processRef = processRef;
        this.pid = pid;
        this.ownerKey = ownerKey;
        this.serverPort = serverPort;
        this.systemPort = systemPort;
        this.chromedriverPort = chromedriverPort;
        this.baseUrl = baseUrl;
    }

    public String getProcessRef() { return processRef; }
    public long getPid() { return pid; }
    public String getOwnerKey() { return ownerKey; }
    public int getServerPort() { return serverPort; }
    public int getSystemPort() { return systemPort; }
    public int getChromedriverPort() { return chromedriverPort; }
    public String getBaseUrl() { return baseUrl; }
}
