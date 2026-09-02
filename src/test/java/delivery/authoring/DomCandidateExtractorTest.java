package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class DomCandidateExtractorTest {
    @Test
    public void extractsDataTestInPriorityOrder() {
        String html = """
                <body>
                  <input id="user-name" data-test="username" name="user-name"/>
                  <a data-test="shopping-cart-link">Cart</a>
                </body>
                """;
        List<DomCandidate> list = DomCandidateExtractor.extract(html);
        Assert.assertFalse(list.isEmpty());
        Assert.assertTrue(list.stream().anyMatch(c -> "id".equals(c.strategy()) && "user-name".equals(c.value())));
        Assert.assertTrue(list.stream().anyMatch(c -> "data-test".equals(c.strategy()) && "shopping-cart-link".equals(c.value())));
        Assert.assertTrue(list.get(0).id().startsWith("c"));
    }

    @Test
    public void formatTableIncludesIds() {
        List<DomCandidate> list = DomCandidateExtractor.extract("<body><button data-test=\"login-button\"/></body>");
        String table = DomCandidateExtractor.formatTable(list);
        Assert.assertTrue(table.contains("login-button"));
        Assert.assertTrue(table.contains("c1"));
    }

    @Test
    public void fallsBackToCssAttrWhenNoStableIdDataTestOrName() {
        String html = """
                <body>
                  <button aria-label="Add lantern to cart">Add</button>
                  <a href="/cart" title="Shopping cart">Cart</a>
                </body>
                """;
        List<DomCandidate> list = DomCandidateExtractor.extract(html);
        Assert.assertTrue(list.stream().anyMatch(c ->
                        "css".equals(c.strategy()) && c.value().contains("aria-label")
                                && c.value().contains("Add lantern to cart")),
                DomCandidateExtractor.formatTable(list));
        Assert.assertTrue(list.stream().anyMatch(c ->
                        "css".equals(c.strategy())
                                && (c.value().contains("href") || c.value().contains("title"))),
                DomCandidateExtractor.formatTable(list));
        Assert.assertTrue(list.stream().noneMatch(c ->
                DomCandidateExtractor.isStableStrategy(c.strategy())
                        && c.value().contains("Add lantern")));
    }

    @Test
    public void prefersStableOverCssDuplicate() {
        String html = """
                <body>
                  <button id="save-btn" aria-label="Save changes">Save</button>
                </body>
                """;
        List<DomCandidate> list = DomCandidateExtractor.extract(html);
        Assert.assertTrue(list.stream().anyMatch(c -> "id".equals(c.strategy()) && "save-btn".equals(c.value())));
        Assert.assertTrue(list.stream().noneMatch(c ->
                "css".equals(c.strategy()) && c.value().contains("Save changes")));
    }
}
