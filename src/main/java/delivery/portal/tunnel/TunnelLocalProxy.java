package delivery.portal.tunnel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local reverse proxy for A5: exposes {@code listenPort} and forwards to {@code targetBaseUrl}.
 * Pair with portal enrollment + heartbeat so the Execute badge shows {@code active}.
 */
public final class TunnelLocalProxy implements AutoCloseable {

    private final HttpServer server;
    private final HttpClient client;
    private final URI targetBase;
    private final ExecutorService pool;

    public TunnelLocalProxy(int listenPort, String targetBaseUrl) throws IOException {
        this.targetBase = URI.create(targetBaseUrl.endsWith("/") ? targetBaseUrl : targetBaseUrl + "/");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.pool = Executors.newFixedThreadPool(8);
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", listenPort), 0);
        this.server.createContext("/", new ProxyHandler());
        this.server.setExecutor(pool);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private static boolean isHopByHopRequestHeader(String name) {
        if (name == null) {
            return true;
        }
        String k = name.toLowerCase();
        return k.equals("host") || k.equals("content-length") || k.equals("connection")
                || k.equals("transfer-encoding") || k.equals("keep-alive")
                || k.equals("proxy-authenticate") || k.equals("proxy-authorization")
                || k.equals("te") || k.equals("trailer") || k.equals("upgrade") || k.equals("expect");
    }

    @Override
    public void close() {
        server.stop(0);
        pool.shutdownNow();
    }

    private final class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getRawPath();
                String query = exchange.getRequestURI().getRawQuery();
                String rel = path == null ? "" : path;
                if (rel.startsWith("/")) {
                    rel = rel.substring(1);
                }
                URI dest = targetBase.resolve(rel);
                if (query != null && !query.isBlank()) {
                    dest = URI.create(dest.toString() + "?" + query);
                }
                HttpRequest.Builder b = HttpRequest.newBuilder(dest)
                        .timeout(Duration.ofSeconds(60))
                        .method(exchange.getRequestMethod(), bodyPublisher(exchange));
                exchange.getRequestHeaders().forEach((k, v) -> {
                    if (!isHopByHopRequestHeader(k)) {
                        v.forEach(hv -> b.header(k, hv));
                    }
                });
                HttpResponse<byte[]> res = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
                exchange.getResponseHeaders().put("X-Keel-Tunnel", java.util.List.of("local-proxy"));
                res.headers().map().forEach((k, values) -> {
                    if (!isHopByHopRequestHeader(k) && values != null && !values.isEmpty()) {
                        exchange.getResponseHeaders().put(k, values);
                    }
                });
                int len = res.body() == null ? 0 : res.body().length;
                boolean noBody = len == 0 || "HEAD".equalsIgnoreCase(exchange.getRequestMethod())
                        || res.statusCode() == 204 || res.statusCode() == 304;
                // -1 = no body; 0 would switch HttpServer to chunked mode (invalid for 204/304/HEAD).
                exchange.sendResponseHeaders(res.statusCode(), noBody ? -1 : len);
                if (!noBody) {
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(res.body());
                    }
                }
                exchange.close();
            } catch (Exception e) {
                byte[] msg = ("Tunnel proxy error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(502, msg.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg);
                }
                exchange.close();
            }
        }

        private HttpRequest.BodyPublisher bodyPublisher(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())
                    || "HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                return HttpRequest.BodyPublishers.noBody();
            }
            try (InputStream in = exchange.getRequestBody()) {
                byte[] body = in.readAllBytes();
                return HttpRequest.BodyPublishers.ofByteArray(body);
            }
        }
    }
}
