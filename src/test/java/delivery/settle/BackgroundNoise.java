package delivery.settle;

import com.sun.net.httpserver.HttpServer;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * r3: deterministic background network noise like the company machine's (Chrome/extension/security-tool
 * traffic the page did not start), injected into the page under test:
 * <ul>
 *   <li>a timer fetch + XHR every 80 ms to ANOTHER origin (a second local server on "localhost");</li>
 *   <li>a timer image load every 90 ms to that origin (resource timing, non-fetch kind);</li>
 *   <li>a fake extension content script (sourceURL chrome-extension://…) fetching the page's OWN origin
 *       every 70 ms: same-origin, so only the initiator check can tell it apart;</li>
 *   <li>a request to a chrome-extension:// URL (always fails; must never count).</li>
 * </ul>
 */
public final class BackgroundNoise {
    private static HttpServer other;

    private BackgroundNoise() {}

    public static synchronized String otherOrigin() {
        if (other == null) {
            try {
                other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                other.setExecutor(Executors.newFixedThreadPool(4));
                other.createContext("/", ex -> {
                    byte[] b = "noise".getBytes();
                    ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    ex.getResponseHeaders().add("Content-Type", "text/plain");
                    ex.sendResponseHeaders(200, b.length);
                    ex.getResponseBody().write(b);
                    ex.close();
                });
                other.start();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return "http://localhost:" + other.getAddress().getPort(); // page runs on 127.0.0.1: different origin
    }

    /** Starts the noise in the current document; returns after the first noise requests went out. */
    public static void inject(WebDriver d) {
        String js = """
                var o = arguments[0];
                window.__noise = [
                  setInterval(function(){ fetch(o + '/beacon?' + Date.now()).catch(function(){});
                    var x = new XMLHttpRequest(); x.open('GET', o + '/xhr?' + Date.now()); x.send(); }, 80),
                  setInterval(function(){ new Image().src = o + '/pixel.gif?' + Date.now(); }, 90)
                ];
                (0, eval)("window.__noise.push(setInterval(function(){ fetch('/ext-ping?' + Date.now()).catch(function(){});"
                  + " fetch('chrome-extension://abcdefghijklmnopabcdefghijklmnop/bg?' + Date.now()).catch(function(){}); }, 70));"
                  + "\\n//# sourceURL=chrome-extension://abcdefghijklmnopabcdefghijklmnop/content.js");
                """;
        ((JavascriptExecutor) d).executeScript(js, otherOrigin());
        try { Thread.sleep(250); } catch (InterruptedException ignored) { }
    }

    /** {counted, ignored} as seen by the page tracker (call after an await). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> counters(WebDriver d) {
        return (Map<String, Object>) ((JavascriptExecutor) d).executeScript(
                "var n=window.__keelNet||{}; return {counted:n.counted||0, ignored:n.ignored||0};");
    }
}
