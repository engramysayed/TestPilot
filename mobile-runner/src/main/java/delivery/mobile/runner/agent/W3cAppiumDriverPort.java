package delivery.mobile.runner.agent;

import delivery.mobile.runner.action.MobileDriverPort;
import delivery.mobile.runner.action.NormalizedSwipe;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import delivery.mobile.runner.snapshot.MobileSnapshotParser;
import org.openqa.selenium.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link MobileDriverPort} over raw W3C / Appium HTTP against an existing session that the portal started
 * (UiAutomator2). Taps use pointer actions at the node's bounds centre; text entry finds the element by its
 * {@code bounds} attribute so a node index from the last snapshot maps to a live element.
 */
public final class W3cAppiumDriverPort implements MobileDriverPort {

    private static final Json JSON = new Json();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String base;
    private final MobileSnapshotParser parser = new MobileSnapshotParser();
    private final AtomicLong seq = new AtomicLong();
    private volatile MobileSnapshot last;
    private volatile int width = 1080;
    private volatile int height = 1920;
    private volatile boolean cancelled;

    public W3cAppiumDriverPort(String appiumBaseUrl, String sessionId) {
        String b = appiumBaseUrl.endsWith("/") ? appiumBaseUrl.substring(0, appiumBaseUrl.length() - 1) : appiumBaseUrl;
        this.base = b + "/session/" + sessionId;
        try {
            Object rect = value(call("GET", "/window/rect", null));
            if (rect instanceof Map<?, ?> m) {
                width = ((Number) m.get("width")).intValue();
                height = ((Number) m.get("height")).intValue();
            }
        } catch (DriverException ignored) {
            // keep defaults; swipes still resolve
        }
    }

    public void cancel() {
        cancelled = true;
    }

    @Override
    public boolean isActionCancelled() {
        return cancelled;
    }

    // ------------------------------------------------------------------ snapshot

    @Override
    public MobileSnapshot pageSource() {
        try {
            String xml = String.valueOf(value(call("GET", "/source", null)));
            String pkg = "";
            String act = "";
            try {
                pkg = String.valueOf(value(call("GET", "/appium/device/current_package", null)));
                act = String.valueOf(value(call("GET", "/appium/device/current_activity", null)));
            } catch (DriverException ignored) {
            }
            MobileSnapshotMeta meta = new MobileSnapshotMeta(seq.incrementAndGet(), System.currentTimeMillis(),
                    pkg, act, "NATIVE", 0, width, height, "", null);
            last = parser.parse(xml, meta);
            return last;
        } catch (DriverException e) {
            throw new IllegalStateException("page source failed: " + e.getMessage(), e);
        }
    }

    private MobileSnapshotNode node(int index) throws DriverException {
        MobileSnapshot s = last;
        if (s == null || index < 0 || index >= s.size()) {
            throw new DriverException(DriverException.Kind.STALE, "node " + index + " not in the last snapshot");
        }
        return s.node(index);
    }

    private String elementAt(int index) throws DriverException {
        MobileSnapshotNode n = node(index);
        var b = n.bounds();
        String xpath = "//*[@bounds='[" + b.left() + "," + b.top() + "][" + b.right() + "," + b.bottom() + "]']";
        Map<String, Object> body = Map.of("using", "xpath", "value", xpath);
        Object v = value(call("POST", "/element", body));
        if (v instanceof Map<?, ?> m) {
            for (Object val : m.values()) {
                return String.valueOf(val); // W3C element reference
            }
        }
        throw new DriverException(DriverException.Kind.STALE, "element not found at bounds " + xpath);
    }

    // ------------------------------------------------------------------ actions

    @Override
    public void tap(int nodeIndex) throws DriverException {
        var b = node(nodeIndex).bounds();
        pointer(b.centerX(), b.centerY(), b.centerX(), b.centerY(), 50);
    }

    @Override
    public void longPress(int nodeIndex) throws DriverException {
        var b = node(nodeIndex).bounds();
        pointer(b.centerX(), b.centerY(), b.centerX(), b.centerY(), 1200);
    }

    @Override
    public void clear(int nodeIndex) throws DriverException {
        call("POST", "/element/" + elementAt(nodeIndex) + "/clear", Map.of());
    }

    @Override
    public void typeUnicode(int nodeIndex, String value) throws DriverException {
        // UiAutomator2 sets text directly (Unicode-safe: Arabic, emoji) — never adb input text.
        call("POST", "/element/" + elementAt(nodeIndex) + "/value", Map.of("text", value == null ? "" : value));
    }

    @Override
    public void typeAscii(int nodeIndex, String value) throws DriverException {
        typeUnicode(nodeIndex, value);
    }

    @Override
    public String readText(int nodeIndex) throws DriverException {
        return String.valueOf(value(call("GET", "/element/" + elementAt(nodeIndex) + "/text", null)));
    }

    @Override
    public byte[] screenshot() throws DriverException {
        return Base64.getDecoder().decode(String.valueOf(value(call("GET", "/screenshot", null))));
    }

    @Override
    public void pressBack() throws DriverException {
        call("POST", "/back", Map.of());
    }

    @Override
    public void pressKeyCode(int keyCode) throws DriverException {
        call("POST", "/appium/device/press_keycode", Map.of("keycode", keyCode));
    }

    @Override
    public boolean isKeyboardShown() {
        try {
            return Boolean.TRUE.equals(value(call("GET", "/appium/device/is_keyboard_shown", null)));
        } catch (DriverException e) {
            return false;
        }
    }

    @Override
    public void swipeNormalized(NormalizedSwipe swipe) throws DriverException {
        int[] px = swipe.toDevicePx(width, height);
        pointer(px[0], px[1], px[2], px[3], px[4]);
    }

    @Override
    public void launchApp(String packageName, String activity) throws DriverException {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("intent", packageName + "/" + activity);
        executeMobile("mobile: startActivity", args);
    }

    @Override
    public void restartApp(String packageName, boolean clearData) throws DriverException {
        executeMobile("mobile: terminateApp", Map.of("appId", packageName));
        if (clearData) {
            executeMobile("mobile: clearApp", Map.of("appId", packageName));
        }
        executeMobile("mobile: activateApp", Map.of("appId", packageName));
    }

    @Override
    public int windowWidth() {
        return width;
    }

    @Override
    public int windowHeight() {
        return height;
    }

    // ------------------------------------------------------------------ HTTP

    private void executeMobile(String script, Map<String, Object> args) throws DriverException {
        call("POST", "/execute/sync", Map.of("script", script, "args", List.of(args)));
    }

    private void pointer(int x1, int y1, int x2, int y2, long holdMs) throws DriverException {
        List<Map<String, Object>> steps = List.of(
                Map.of("type", "pointerMove", "duration", 0, "x", x1, "y", y1),
                Map.of("type", "pointerDown", "button", 0),
                Map.of("type", "pause", "duration", Math.max(0, holdMs)),
                Map.of("type", "pointerMove", "duration", x1 == x2 && y1 == y2 ? 0 : (int) Math.max(100, holdMs), "x", x2, "y", y2),
                Map.of("type", "pointerUp", "button", 0));
        Map<String, Object> action = Map.of("type", "pointer", "id", "finger1",
                "parameters", Map.of("pointerType", "touch"), "actions", steps);
        call("POST", "/actions", Map.of("actions", List.of(action)));
    }

    private Map<String, Object> call(String method, String path, Object body) throws DriverException {
        if (cancelled) {
            throw new DriverException(DriverException.Kind.CANCELLED, "run cancelled");
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json; charset=utf-8");
        if ("GET".equals(method)) {
            b.GET();
        } else {
            b.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : JSON.toJson(body), StandardCharsets.UTF_8));
        }
        HttpResponse<String> res;
        try {
            res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new DriverException(DriverException.Kind.TRANSPORT, "Appium unreachable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriverException(DriverException.Kind.CANCELLED, "interrupted");
        }
        Map<String, Object> parsed = JSON.toType(res.body() == null || res.body().isBlank() ? "{}" : res.body(), Json.MAP_TYPE);
        if (res.statusCode() >= 400) {
            Object v = parsed.get("value");
            String err = v instanceof Map<?, ?> m ? String.valueOf(m.get("error")) : "";
            String msg = v instanceof Map<?, ?> m ? String.valueOf(m.get("message")) : res.body();
            DriverException.Kind kind = switch (err) {
                case "stale element reference", "no such element" -> DriverException.Kind.STALE;
                case "element not interactable" -> DriverException.Kind.HIDDEN;
                case "invalid session id" -> DriverException.Kind.TRANSPORT;
                default -> DriverException.Kind.OTHER;
            };
            throw new DriverException(kind, err + ": " + msg);
        }
        return parsed;
    }

    private static Object value(Map<String, Object> response) {
        return response == null ? null : response.get("value");
    }
}
