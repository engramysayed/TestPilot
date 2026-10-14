package delivery.mobile.transport;

import org.testng.Assert;
import org.testng.annotations.Test;

/** M1-05: token / secret envelope redaction. */
public class RunnerTransportRedactorTest {

    @Test
    public void redactsBearerAndTpRunTokens() {
        String raw = "Authorization: Bearer tp_run_abcdefghijklmnopqrstuv error";
        String out = RunnerTransportRedactor.redact(raw);
        Assert.assertFalse(out.contains("tp_run_abcdefghijklmnopqrstuv"), out);
        Assert.assertTrue(out.contains(RunnerTransportRedactor.MASK), out);
        Assert.assertFalse(RunnerTransportRedactor.containsTokenLeak(out));
    }

    @Test
    public void redactsSecretEnvelopeFields() {
        String raw = "{\"ciphertext\":\"AAAA\",\"mac\":\"BBBB\",\"secretEnvelopeRef\":\"sec_abc\"}";
        String out = RunnerTransportRedactor.redact(raw);
        Assert.assertFalse(out.contains("AAAA"), out);
        Assert.assertFalse(out.contains("BBBB"), out);
        Assert.assertTrue(out.contains("\"ciphertext\":\"" + RunnerTransportRedactor.MASK + "\""), out);
    }

    @Test
    public void redactsSignatureHeaders() {
        String raw = "X-Keel-Signature: v1=deadbeefcafebabe";
        String out = RunnerTransportRedactor.redact(raw);
        Assert.assertFalse(out.contains("deadbeef"), out);
        Assert.assertTrue(out.contains(RunnerTransportRedactor.MASK), out);
    }
}
