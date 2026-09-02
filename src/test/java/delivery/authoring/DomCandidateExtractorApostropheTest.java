package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class DomCandidateExtractorApostropheTest {

    @Test
    public void extractorKeepsApostropheInButtonTextXpath() {
        String html = "<html><body><button>It's gone</button></body></html>";
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        boolean found = cs.stream().anyMatch(c ->
                "xpath".equalsIgnoreCase(c.strategy())
                        && c.value() != null
                        && c.value().contains("concat(")
                        && c.value().toLowerCase().contains("gone"));
        Assert.assertTrue(found, cs.toString());
    }
}
