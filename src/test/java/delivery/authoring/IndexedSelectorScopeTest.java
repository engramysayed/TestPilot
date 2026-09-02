package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Indexed xpath must scope to form-control tag (and input type when present), never to generic
 * containers whose document-order position is unstable across pages.
 */
public class IndexedSelectorScopeTest {

    private static final String MIXED_INPUTS = """
            <html><body>
              <div>Header block</div>
              <span>Username</span><input type="text">
              <div>Spacer</div>
              <input type="checkbox"> agree
              <span>Email</span><input type="email">
            </body></html>
            """;

    private static final String DUPLICATE_CAPTION_DIVS = """
            <html><body>
              <div>Sidebar</div>
              <span>Option A</span><div onclick="pickA()"></div>
              <span>Option B</span><div onclick="pickB()"></div>
            </body></html>
            """;

    @Test
    public void indexedInputIsScopedToTypeNotAllTags() {
        List<DomCandidate> c = DomCandidateExtractor.extract(MIXED_INPUTS);

        Assert.assertTrue(c.stream().anyMatch(x ->
                        "xpath".equals(x.strategy())
                                && "(//input[@type='checkbox'])[1]".equals(x.value())),
                "checkbox ordinal must be type-scoped: " + DomCandidateExtractor.formatTable(c));

        DomCandidate username = c.stream()
                .filter(x -> "xpath".equals(x.strategy()) && x.label().contains("Username"))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(username, DomCandidateExtractor.formatTable(c));
        Assert.assertEquals(username.value(), "(//input[@type='text'])[1]",
                "text input must index among typed inputs, not all tags");

        DomCandidate email = c.stream()
                .filter(x -> "xpath".equals(x.strategy()) && x.label().contains("Email"))
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(email, DomCandidateExtractor.formatTable(c));
        Assert.assertEquals(email.value(), "(//input[@type='email'])[1]",
                "email input must index among email inputs only");

        Assert.assertTrue(c.stream().noneMatch(x ->
                        "xpath".equals(x.strategy()) && x.value().startsWith("(//div)[")),
                "must not emit global div index: " + DomCandidateExtractor.formatTable(c));
    }

    @Test
    public void neverEmitsIndexedXpathForGenericContainers() {
        List<DomCandidate> c = DomCandidateExtractor.extract(DUPLICATE_CAPTION_DIVS);

        Assert.assertTrue(c.stream().noneMatch(x ->
                        "xpath".equals(x.strategy()) && x.value().startsWith("(//div)[")),
                "div must not get document-order xpath: " + DomCandidateExtractor.formatTable(c));
        Assert.assertTrue(c.stream().noneMatch(x ->
                        "xpath".equals(x.strategy()) && x.value().startsWith("(//span)[")),
                "span must not get document-order xpath: " + DomCandidateExtractor.formatTable(c));
        Assert.assertTrue(c.stream().noneMatch(x ->
                        "xpath".equals(x.strategy()) && x.value().startsWith("(//section)[")),
                "section must not get document-order xpath");
    }
}
