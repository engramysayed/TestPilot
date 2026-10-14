package delivery.mobile.appium;

/**
 * AE-L6: injectable W3C NewSession / DeleteSession over raw HTTP (no Appium java-client).
 */
public interface AppiumSessionTransporter {
    /**
     * POST {@code {baseUrl}/session} with W3C capabilities; return Appium session id.
     *
     * @throws AppiumLifecycleException on non-loopback baseUrl, HTTP error, or missing sessionId
     */
    String createSession(String baseUrl, String deviceSerial) throws AppiumLifecycleException;

    /**
     * DELETE {@code {baseUrl}/session/{sessionId}} best-effort. Implementations should not throw
     * for ordinary network failures (bookkeeping still marks QUIT).
     */
    void deleteSession(String baseUrl, String sessionId);
}
