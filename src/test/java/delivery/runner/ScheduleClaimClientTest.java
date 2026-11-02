package delivery.runner;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduleClaimClientTest {

    @Test
    public void listDueAndStartRun() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/schedules/due", ex -> {
            byte[] body = """
                    [{"projectId":"proj1","suiteId":"suite-1","tcIds":["TC_1"],"alertChannelId":"slack",
                      "schedule":{"enabled":true,"kind":"interval","intervalMinutes":10}}]
                    """.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/api/projects/proj1/suites/suite-1/runs", ex -> {
            posts.incrementAndGet();
            byte[] req = ex.getRequestBody().readAllBytes();
            JSONObject o = new JSONObject(new String(req, StandardCharsets.UTF_8));
            Assert.assertEquals(o.getString("trigger"), "schedule");
            byte[] body = "{\"jobId\":\"exec_abc\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(201, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            URI portal = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            ScheduleClaimClient client = new ScheduleClaimClient(portal, "tp_run_test");
            List<ScheduleClaimClient.DueItem> due = client.listDue(60);
            Assert.assertEquals(due.size(), 1);
            Assert.assertEquals(due.get(0).suiteId(), "suite-1");
            Assert.assertEquals(due.get(0).tcIds(), List.of("TC_1"));
            int started = client.claimAndStartDue(60);
            Assert.assertEquals(started, 1);
            Assert.assertEquals(posts.get(), 1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void due404YieldsEmpty() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/schedules/due", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        try {
            URI portal = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            ScheduleClaimClient client = new ScheduleClaimClient(portal, "tp_run_test");
            Assert.assertTrue(client.listDue(30).isEmpty());
            Assert.assertEquals(client.claimAndStartDue(30), 0);
        } finally {
            server.stop(0);
        }
    }
}
