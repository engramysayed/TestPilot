package delivery.mobile.runner.precision;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class MobilePrecisionGateTest {

    private static PrecisionCandidate user() {
        return new PrecisionCandidate("c-user", "NATIVE", true);
    }

    private static PrecisionCandidate other() {
        return new PrecisionCandidate("c-other", "NATIVE", false);
    }

    private static PrecisionInput base(String chosen, String response) {
        return new PrecisionInput(
                List.of(user(), other()),
                chosen,
                "NATIVE",
                0,
                3,
                true,
                true,
                "cursor",
                response,
                false);
    }

    @Test
    public void validSingleCandidateIsChosen() {
        PrecisionOutcome out = MobilePrecisionGate.rank(base("c-user", "{\"candidateId\":\"c-user\"}"));
        assertEquals(out.usedProvider(), "cursor");
        assertEquals(out.chosenId(), "c-user");
        assertEquals(out.fallbackReason(), "");
    }

    @Test
    public void invalidIdRejected() {
        assertEquals(MobilePrecisionGate.rank(base("c-missing", "{\"candidateId\":\"c-missing\"}")).code(),
                "INVALID_ID");
    }

    @Test
    public void wrongContextRejected() {
        PrecisionCandidate web = new PrecisionCandidate("c-user", "WEBVIEW", true);
        PrecisionInput in = new PrecisionInput(List.of(web), "c-user", "NATIVE", 0, 3, true, true,
                "cursor", "{\"candidateId\":\"c-user\"}", false);
        assertEquals(MobilePrecisionGate.rank(in).code(), "WRONG_CONTEXT");
    }

    @Test
    public void unavailableDisallowedAndExhaustedFallBackToKeel() {
        PrecisionInput unavailable = new PrecisionInput(List.of(user()), "c-user", "NATIVE", 0, 3,
                false, true, "cursor", "{}", false);
        PrecisionOutcome a = MobilePrecisionGate.rank(unavailable);
        assertEquals(a.usedProvider(), "keel");
        assertEquals(a.fallbackReason(), "UNAVAILABLE");
        assertFalse(a.countsAsCursor());

        PrecisionInput disallowed = new PrecisionInput(List.of(user()), "c-user", "NATIVE", 0, 3,
                true, false, "cursor", "{}", false);
        assertEquals(MobilePrecisionGate.rank(disallowed).fallbackReason(), "DISALLOWED");

        PrecisionInput exhausted = new PrecisionInput(List.of(user()), "c-user", "NATIVE", 3, 3,
                true, true, "cursor", "{}", false);
        assertEquals(MobilePrecisionGate.rank(exhausted).fallbackReason(), "EXHAUSTED");
    }

    @Test
    public void secretInResponseIsRejected() {
        assertEquals(MobilePrecisionGate.rank(base("c-user", "password=SECRET_CANARY_OTP_999888")).code(),
                "SECRET_REDACTION");
    }

    @Test
    public void userOverrideWinsOverOtherId() {
        assertEquals(MobilePrecisionGate.rank(base("c-other", "{\"candidateId\":\"c-other\"}")).code(),
                "USER_OVERRIDE");
    }

    @Test
    public void codeOrShellResponseIsRejected() {
        assertEquals(MobilePrecisionGate.rank(base("c-user", "Runtime.getRuntime().exec(\"rm\")")).code(),
                "CODE_REJECTED");
    }

    @Test
    public void batchResponseIsRejected() {
        PrecisionInput batch = new PrecisionInput(List.of(user()), "c-user", "NATIVE", 0, 3,
                true, true, "cursor", "{\"candidateId\":\"c-user\"}", true);
        assertEquals(MobilePrecisionGate.rank(batch).code(), "BATCH_REJECTED");
    }
}
