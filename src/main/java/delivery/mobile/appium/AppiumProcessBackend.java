package delivery.mobile.appium;

/**
 * Starts/stops a per-job Appium <em>CLI</em> process. Implementations must not put
 * Appium Java-client jars on the portal classpath and must never kill unrelated PIDs
 * or run {@code adb kill-server}.
 */
public interface AppiumProcessBackend {
    /** Probe whether the Appium executable / harness is available (no side effects). */
    boolean isAvailable();

    /**
     * Start Appium bound to the owned loopback ports in {@code request}.
     * Must honour cancel-while-starting and only track the returned owned process.
     */
    OwnedAppiumProcess start(AppiumStartRequest request) throws AppiumLifecycleException;

    /** Stop only the given owned process (and its process tree). Never kill unrelated PIDs. */
    void stop(OwnedAppiumProcess process) throws AppiumLifecycleException;

    boolean isAlive(OwnedAppiumProcess process);
}
