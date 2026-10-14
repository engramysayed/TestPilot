package delivery.portal.preflight;

import delivery.net.TargetNetworkPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * One GET to the project base URL, checked against {@link TargetNetworkPolicy} first (URL and resolved
 * addresses), no redirects followed, short timeout. Any HTTP status counts as reachable.
 */
@Component
public class HttpPreflightProbe implements PreflightProbe {
    private final Duration timeout;
    private final TargetNetworkPolicyFactory policies;

    /** Indirection so tests can pass a dedicated policy. */
    public interface TargetNetworkPolicyFactory {
        TargetNetworkPolicy forBaseUrl(String baseUrl);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public HttpPreflightProbe(@Value("${keel.preflight.timeout-ms:5000}") long timeoutMs) {
        this(Duration.ofMillis(timeoutMs), TargetNetworkPolicy::forJob);
    }

    public HttpPreflightProbe(Duration timeout, TargetNetworkPolicyFactory policies) {
        this.timeout = timeout;
        this.policies = policies;
    }

    @Override
    public Result probe(String baseUrl) {
        TargetNetworkPolicy policy;
        URI uri;
        try {
            policy = policies.forBaseUrl(baseUrl);
            uri = URI.create(baseUrl.trim());
        } catch (RuntimeException e) {
            return new Result(false, null, null, "The website address is not valid.");
        }
        TargetNetworkPolicy.Decision d = policy.inspect(baseUrl);
        if (!d.allowed()) {
            return new Result(false, null, null, "Keel is not allowed to open this address (" + d.reason() + ").");
        }
        try {
            List<InetAddress> addrs = Arrays.asList(InetAddress.getAllByName(uri.getHost()));
            TargetNetworkPolicy.Decision r = policy.inspectResolved(uri.getHost(), addrs);
            if (!r.allowed()) {
                return new Result(false, null, null, "Keel is not allowed to open this address (" + r.reason() + ").");
            }
        } catch (Exception e) {
            return new Result(false, null, null, "Keel could not find this website (DNS lookup failed).");
        }
        long start = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpResponse<Void> resp = client.send(
                    HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Result(true, resp.statusCode(), ms, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, null, null, "The website check was interrupted.");
        } catch (Exception e) {
            return new Result(false, null, null, "Keel could not open the website (no response within "
                    + timeout.toSeconds() + " s).");
        }
    }
}
