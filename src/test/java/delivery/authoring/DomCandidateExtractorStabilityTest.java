package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class DomCandidateExtractorStabilityTest {

    @Test
    public void roleAndAriaLabelEmittedWhenNoTestId() {
        String html = "<html><body><div role=\"button\" aria-label=\"Export csv\">X</div></body></html>";
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        Assert.assertTrue(cs.stream().anyMatch(c ->
                c.value().contains("role") && c.value().toLowerCase().contains("export")),
                cs.toString());
    }

    @Test
    public void hashedClassIsNotTheLocator() {
        String html = "<html><body><button class=\"css-1ab2cde primary\">Go</button></body></html>";
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        Assert.assertFalse(cs.stream().anyMatch(c ->
                "css".equals(c.strategy()) && c.value().contains("css-1ab2cde")),
                cs.toString());
    }

    @Test
    public void generatedIdNotEmittedAsIdStrategy() {
        String html = "<html><body><button id=\"ember1234\" aria-label=\"Refresh\">R</button></body></html>";
        List<DomCandidate> cs = DomCandidateExtractor.extract(html);
        Assert.assertFalse(cs.stream().anyMatch(c -> "id".equals(c.strategy()) && "ember1234".equals(c.value())),
                cs.toString());
    }
}