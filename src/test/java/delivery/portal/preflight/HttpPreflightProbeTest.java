package delivery.portal.preflight;

import com.sun.net.httpserver.HttpServer;
import delivery.net.TargetNetworkPolicy;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

/** B14: the probe respects TargetNetworkPolicy before opening any connection. */
public class HttpPreflightProbeTest {

    @Test
    public void sharedPolicyBlocksLoopbackWithoutConnecting() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int[] hits = {0};
        server.createContext("/", ex -> { hits[0]++; ex.sendResponseHeaders(200, -1); ex.close(); });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            PreflightProbe.Result r = new HttpPreflightProbe(Duration.ofSeconds(2), TargetNetworkPolicy::shared).probe(url);
            Assert.assertEquals(r.reachable(), Boolean.FALSE);
            Assert.assertTrue(r.message().contains("not allowed"), r.message());
            Assert.assertEquals(hits[0], 0, "blocked address must not be contacted");

            PreflightProbe.Result ok = new HttpPreflightProbe(Duration.ofSeconds(2),
                    u -> TargetNetworkPolicy.dedicated(u, List.of("127.0.0.0/8"))).probe(url);
            Assert.assertEquals(ok.reachable(), Boolean.TRUE, String.valueOf(ok.message()));
            Assert.assertEquals(ok.httpStatus(), Integer.valueOf(200));
            Assert.assertEquals(hits[0], 1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void invalidUrlIsUnreachable() {
        PreflightProbe.Result r = new HttpPreflightProbe(Duration.ofSeconds(1), TargetNetworkPolicy::shared).probe("::not a url");
        Assert.assertEquals(r.reachable(), Boolean.FALSE);
    }
}
