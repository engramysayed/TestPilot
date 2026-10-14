package delivery.evidence;

import delivery.snapshot.TextLocatorSanitizer;
import org.testng.annotations.Test;

import java.util.Set;

import static org.testng.Assert.*;

public class BindFailureEvidenceTest {
    @Test public void redactMasksPii() {
        assertEquals(TextLocatorSanitizer.redact("mail john.doe@example.com now"), "mail [email] now");
        assertEquals(TextLocatorSanitizer.redact("call 0100 123 4567"), "call [digits]");
        assertEquals(TextLocatorSanitizer.redact("IBAN EG380019000500000000263180002"), "IBAN [iban]");
        assertEquals(TextLocatorSanitizer.redact("Hello, Sayed!"), "Hello, [name]!");
        assertEquals(TextLocatorSanitizer.redact("Order #12345 Save"), "Order #12345 Save");
        assertFalse(TextLocatorSanitizer.containsPii("Submit order"));
    }

    @Test public void htmlRedactionKeepsStructure() {
        String r = BindFailureEvidence.redactHtml("<a title=\"x@y.io\">mail x@y.io</a><p>1234567</p>");
        assertEquals(r, "<a title=\"[email]\">mail [email]</a><p>[digits]</p>");
    }

    @Test public void typoAndTokens() {
        assertEquals(BindFailureEvidence.edit("sumbit", "submit"), 1);
        assertEquals(BindFailureEvidence.edit("payment", "paymnet"), 1);
        assertNotNull(BindFailureEvidence.bestTypo(Set.of("sumbit"), Set.of("submit", "order")));
        assertNull(BindFailureEvidence.bestTypo(Set.of("cat"), Set.of("car")), "short tokens never count as typos");
        assertEquals(BindFailureEvidence.tokens("Click the Submit button"), Set.of("submit"));
    }
}
