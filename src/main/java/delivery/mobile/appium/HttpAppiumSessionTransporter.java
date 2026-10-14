package delivery.mobile.appium;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AE-L6: raw HTTP W3C NewSession / DeleteSession against owned loopback Appium only.
 * Same style as AE-L2 {@link ProcessAppiumBackend#httpStatusReady} — no java-client jars.
 */
public final class HttpAppiumSessionTransporter implements AppiumSessionTransporter {
    private static final Pattern SESSION_ID_IN_VALUE = Pattern.compile(
            "\"sessionId\"\\s*:\\s*\"([^\"\\\\]+)\"");
    private static final int CONNECT_MS = 5_000;
    private static final int READ_MS = 30_000;

    @Override
    public String createSession(String baseUrl, String deviceSerial) throws AppiumLifecycleException {
        String serial = AppiumW3cCapabilities.requireSerial(deviceSerial);
        String base = requireOwnedLoopback(baseUrl);
        String body = AppiumW3cCapabilities.newSessionBody(serial);
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(base + "/session");
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            int code = conn.getResponseCode();
            String responseBody = readBody(conn, code);
            if (code < 200 || code >= 300) {
                throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                        "NewSession HTTP " + code + " body=" + truncate(responseBody));
            }
            String sessionId = parseSessionId(responseBody);
            if (sessionId == null || sessionId.isBlank()) {
                throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                        "NewSession response missing sessionId: " + truncate(responseBody));
            }
            return sessionId;
        } catch (AppiumLifecycleException e) {
            throw e;
        } catch (Exception e) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                    "NewSession failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @Override
    public void deleteSession(String baseUrl, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (!ProcessAppiumBackend.isOwnedLoopbackBaseUrl(baseUrl)) {
            return;
        }
        HttpURLConnection conn = null;
        try {
            String base = trimSlash(baseUrl);
            URI uri = URI.create(base + "/session/" + sessionId.trim());
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(2_000);
            conn.setReadTimeout(5_000);
            conn.setRequestMethod("DELETE");
            conn.setInstanceFollowRedirects(false);
            conn.getResponseCode(); // best-effort
        } catch (Exception ignored) {
            // bookkeeping still marks QUIT
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Parse Appium 2 {@code value.sessionId}, nested value shapes, or Appium 1 top-level {@code sessionId}.
     */
    static String parseSessionId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        int valueIdx = body.indexOf("\"value\"");
        if (valueIdx >= 0) {
            String fromValue = body.substring(valueIdx);
            Matcher m = SESSION_ID_IN_VALUE.matcher(fromValue);
            if (m.find()) {
                return m.group(1);
            }
        }
        Matcher top = SESSION_ID_IN_VALUE.matcher(body);
        if (top.find()) {
            return top.group(1);
        }
        return null;
    }

    static String requireOwnedLoopback(String baseUrl) {
        if (!ProcessAppiumBackend.isOwnedLoopbackBaseUrl(baseUrl)) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                    "NewSession refused: baseUrl must be owned loopback (http://127.0.0.1: or http://localhost:), got="
                            + baseUrl);
        }
        return trimSlash(baseUrl);
    }

    private static String trimSlash(String baseUrl) {
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String readBody(HttpURLConnection conn, int code) {
        try {
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) {
                return "";
            }
            try (InputStream stream = in) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() > 240 ? t.substring(0, 240) + "…" : t;
    }
}
