package delivery.portal.api;

import com.sun.net.httpserver.HttpServer;
import delivery.portal.PortalApplication;
import delivery.portal.tunnel.TunnelAgentExecutor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertTrue;

@SpringBootTest(
        classes = PortalApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
        "spring.datasource.url=jdbc:h2:mem:tunnel-broker;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.tunnel.enabled=true",
        "delivery.tunnel.public-base-url=http://127.0.0.1:8080",
        "delivery.store-root=./target/test-delivery-store-tunnel-broker",
        "delivery.work-dir=./target/test-delivery-work-tunnel-broker",
        })
@AutoConfigureMockMvc
public class TunnelBrokerApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    private HttpServer upstream;
    private int upstreamPort;
    private Thread agent;

    @BeforeMethod
    public void clean() throws Exception {
        Path root = Path.of("./target/test-delivery-store-tunnel-broker");
        if (Files.exists(root)) {
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        upstream = HttpServer.create(new InetSocketAddress(0), 0);
        upstreamPort = upstream.getAddress().getPort();
        upstream.createContext("/", exchange -> {
            byte[] body = "tunnel-ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        upstream.setExecutor(Executors.newCachedThreadPool());
        upstream.start();
    }

    @AfterMethod
    public void stopAgent() {
        if (agent != null) {
            agent.interrupt();
        }
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    private String createProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tunnel\",\"baseUrl\":\"https://example.com/\"}"))
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }

    @Test
    public void publicProxy_relaysThroughAgent() throws Exception {
        String projectId = createProject();
        MvcResult enroll = mockMvc.perform(post("/api/projects/" + projectId + "/tunnel/enroll")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject doc = new JSONObject(enroll.getResponse().getContentAsString());
        String token = doc.getString("token");
        String agentSecret = doc.getString("agentSecret");

        String listenUrl = "http://127.0.0.1:" + upstreamPort;
        mockMvc.perform(post("/api/projects/" + projectId + "/tunnel/heartbeat")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new JSONObject()
                                .put("token", token)
                                .put("listenUrl", listenUrl).toString()))
                .andExpect(status().isOk());

        agent = new Thread(() -> {
            try {
                new TunnelAgentExecutor("http://127.0.0.1:" + port, "")
                        .runLoop(projectId, token, agentSecret, listenUrl);
            } catch (Exception ignored) {
            }
        }, "tunnel-agent-test");
        agent.setDaemon(true);
        agent.start();
        Thread.sleep(500);

        MvcResult proxied = mockMvc.perform(get("/tunnel/p/" + token + "/smoke.txt"))
                .andExpect(status().isOk())
                .andReturn();
        String body = proxied.getResponse().getContentAsString();
        assertTrue(body.contains("tunnel-ok"), "body was: " + body);
    }

    @Test
    public void enroll_reusesWhenCurrentCredentialsPresented() throws Exception {
        String projectId = createProject();
        JSONObject first = enroll(projectId, "{}");
        String token = first.getString("token");
        String secret = first.getString("agentSecret");

        JSONObject again = enroll(projectId, new JSONObject()
                .put("reuseToken", token).put("agentSecret", secret).toString());
        org.testng.Assert.assertEquals(again.getString("token"), token);
        org.testng.Assert.assertTrue(again.getBoolean("reused"));

        JSONObject wrong = enroll(projectId, new JSONObject()
                .put("reuseToken", token).put("agentSecret", "not-the-secret").toString());
        org.testng.Assert.assertNotEquals(wrong.getString("token"), token, "bad secret must mint a new token");
        org.testng.Assert.assertFalse(wrong.getBoolean("reused"));
        org.testng.Assert.assertFalse(Files.exists(Path.of("./target/test-delivery-store-tunnel-broker")
                .resolve("tunnel-index").resolve(token + ".json")), "superseded token index removed");
    }

    @Test
    public void publicProxy_forwardsQueryString() throws Exception {
        upstream.removeContext("/");
        upstream.createContext("/", exchange -> {
            byte[] body = String.valueOf(exchange.getRequestURI().getRawQuery()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        String projectId = createProject();
        JSONObject doc = enroll(projectId, "{}");
        String token = doc.getString("token");
        String listenUrl = "http://127.0.0.1:" + upstreamPort;
        mockMvc.perform(post("/api/projects/" + projectId + "/tunnel/heartbeat")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new JSONObject().put("token", token).put("listenUrl", listenUrl).toString()))
                .andExpect(status().isOk());
        String secret = doc.getString("agentSecret");
        agent = new Thread(() -> {
            try {
                new TunnelAgentExecutor("http://127.0.0.1:" + port, "").runLoop(projectId, token, secret, listenUrl);
            } catch (Exception ignored) {
            }
        }, "tunnel-agent-query-test");
        agent.setDaemon(true);
        agent.start();
        Thread.sleep(500);
        MvcResult proxied = mockMvc.perform(get("/tunnel/p/" + token + "/search?q=ab&page=2"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(proxied.getResponse().getContentAsString().contains("q=ab&page=2"),
                "query lost: " + proxied.getResponse().getContentAsString());
    }

    private JSONObject enroll(String projectId, String body) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects/" + projectId + "/tunnel/enroll")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString());
    }

    @Test
    public void publicProxy_keepsEverySetCookie() throws Exception {
        upstream.removeContext("/");
        upstream.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "sid=1; Path=/");
            exchange.getResponseHeaders().add("Set-Cookie", "csrf=2; Path=/");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        String projectId = createProject();
        JSONObject doc = enroll(projectId, "{}");
        String token = doc.getString("token");
        String listenUrl = "http://127.0.0.1:" + upstreamPort;
        mockMvc.perform(post("/api/projects/" + projectId + "/tunnel/heartbeat")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new JSONObject().put("token", token).put("listenUrl", listenUrl).toString()))
                .andExpect(status().isOk());
        String secret = doc.getString("agentSecret");
        agent = new Thread(() -> {
            try {
                new TunnelAgentExecutor("http://127.0.0.1:" + port, "").runLoop(projectId, token, secret, listenUrl);
            } catch (Exception ignored) {
            }
        }, "tunnel-agent-cookie-test");
        agent.setDaemon(true);
        agent.start();
        Thread.sleep(500);
        MvcResult proxied = mockMvc.perform(post("/tunnel/p/" + token + "/login"))
                .andReturn();
        java.util.List<String> cookies = proxied.getResponse().getHeaders("Set-Cookie");
        assertTrue(cookies.size() == 2, "Set-Cookie collapsed: " + cookies);
    }
}
