package delivery.portal.security;

import keel.contract.runner.RequestSigner;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static delivery.portal.security.RunnerRequestVerifier.Result.*;
import static org.testng.Assert.assertEquals;

/** B06 signature rules: valid, bad signature, clock skew, replay, body tamper, missing headers. */
public class RunnerRequestVerifierTest {
    static final String TOKEN = "tp_run_test";
    static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    static final byte[] BODY = "{\"stage\":\"BROWSER\"}".getBytes(StandardCharsets.UTF_8);
    static final String PATH = "/api/v2/runners/jobs/j1/stage";
    RunnerRequestVerifier v;

    @BeforeMethod
    public void fresh() {
        v = new RunnerRequestVerifier(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    }

    private static String sig(long ts, String nonce, byte[] body) {
        return RequestSigner.sign(TOKEN, "POST", PATH, null, ts, nonce, body);
    }

    private RunnerRequestVerifier.Result check(long ts, String nonce, byte[] body, String sig) {
        return v.verify(TOKEN, "POST", PATH, null, String.valueOf(ts), nonce, body, sig);
    }

    @Test
    public void validSignatureAccepted() {
        long ts = NOW.getEpochSecond();
        assertEquals(check(ts, "n-1", BODY, sig(ts, "n-1", BODY)), OK);
    }

    @Test
    public void badSignatureRejectedAndDoesNotBurnNonce() {
        long ts = NOW.getEpochSecond();
        assertEquals(check(ts, "n-2", BODY, "v1=" + "0".repeat(64)), BAD_SIGNATURE);
        assertEquals(v.verify("tp_run_other", "POST", PATH, null, String.valueOf(ts), "n-2", BODY, sig(ts, "n-2", BODY)),
                BAD_SIGNATURE);
        assertEquals(check(ts, "n-2", BODY, sig(ts, "n-2", BODY)), OK, "nonce still usable by the real runner");
    }

    @Test
    public void clockSkewBeyondFiveMinutesRejected() {
        long past = NOW.minus(Duration.ofMinutes(5)).minusSeconds(1).getEpochSecond();
        long future = NOW.plus(Duration.ofMinutes(5)).plusSeconds(1).getEpochSecond();
        long edge = NOW.minus(Duration.ofMinutes(5)).getEpochSecond();
        assertEquals(check(past, "n-3", BODY, sig(past, "n-3", BODY)), SKEW);
        assertEquals(check(future, "n-4", BODY, sig(future, "n-4", BODY)), SKEW);
        assertEquals(check(edge, "n-5", BODY, sig(edge, "n-5", BODY)), OK);
    }

    @Test
    public void replayRejected() {
        long ts = NOW.getEpochSecond();
        String s = sig(ts, "n-6", BODY);
        assertEquals(check(ts, "n-6", BODY, s), OK);
        assertEquals(check(ts, "n-6", BODY, s), REPLAY);
    }

    @Test
    public void bodyTamperRejected() {
        long ts = NOW.getEpochSecond();
        String s = sig(ts, "n-7", BODY);
        assertEquals(check(ts, "n-7", "{\"stage\":\"EMIT\"}".getBytes(StandardCharsets.UTF_8), s), BAD_SIGNATURE);
        assertEquals(v.verify(TOKEN, "POST", PATH + "x", null, String.valueOf(ts), "n-7", BODY, s), BAD_SIGNATURE);
    }

    @Test
    public void missingOrMalformedHeadersRejected() {
        long ts = NOW.getEpochSecond();
        assertEquals(v.verify(TOKEN, "POST", PATH, null, null, "n", BODY, sig(ts, "n", BODY)), MISSING);
        assertEquals(v.verify(TOKEN, "POST", PATH, null, String.valueOf(ts), null, BODY, sig(ts, "n", BODY)), MISSING);
        assertEquals(v.verify(TOKEN, "POST", PATH, null, String.valueOf(ts), "n", BODY, null), MISSING);
        assertEquals(v.verify(TOKEN, "POST", PATH, null, String.valueOf(ts), "x".repeat(200), BODY, "v1=00"), MISSING);
        assertEquals(v.verify(TOKEN, "POST", PATH, null, "yesterday", "n", BODY, "v1=00"), BAD_TIMESTAMP);
    }
}
