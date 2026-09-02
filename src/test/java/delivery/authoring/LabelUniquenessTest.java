package delivery.authoring;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Label-anchored xpath must gate on exact normalized label text, not substring :contains,
 * so a distinct longer label is not blocked by a shorter label that happens to appear inside it.
 */
public class LabelUniquenessTest {

    @Test
    public void shortNameDoesNotBlockDistinctFirstNameLabel() {
        String html = "<html><body>"
                + "<label>Name<input id='n'/></label>"
                + "<label>First Name<input id='fn'/></label>"
                + "</body></html>";
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        boolean anchored = cs.stream().anyMatch(c ->
                "xpath".equalsIgnoreCase(c.strategy())
                        && c.value() != null
                        && c.value().contains("First Name")
                        && c.value().contains("label"));
        assertTrue(anchored, cs.toString());
    }

    @Test
    public void shortNameLabelAnchorUsesExactEqualityNotContains() {
        String html = "<html><body>"
                + "<label>Name<input id='n'/></label>"
                + "<label>First Name<input id='fn'/></label>"
                + "</body></html>";
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        DomCandidate name = cs.stream()
                .filter(c -> "xpath".equalsIgnoreCase(c.strategy()) && "Name".equals(c.label()))
                .findFirst()
                .orElse(null);
        assertTrue(name != null, "Name field must have label-anchored xpath, got: " + cs);
        assertTrue(name.value().contains("normalize-space(.)="), name.value());
        assertFalse(name.value().contains("contains(normalize-space"),
                "must not use contains — would match First Name: " + name.value());
    }
}
