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
        // Test hooks win — do not also emit id/name twins of the same control.
        Assert.assertTrue(list.stream().anyMatch(c ->
                "data-test".equals(c.strategy()) && "username".equals(c.value())));
        Assert.assertTrue(list.stream().noneMatch(c ->
                "id".equals(c.strategy()) && "user-name".equals(c.value())));
        Assert.assertTrue(list.stream().anyMatch(c ->
                "data-test".equals(c.strategy()) && "shopping-cart-link".equals(c.value())));
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
    public void preferredHookBeatsIdOnTheSameButton() {
        String html = """
                <body>
                  <button id="x" data-axis-test-id="verify_Otp_Button">
                    <span>Verify OTP</span>
                  </button>
                </body>
                """;
        List<DomCandidate> list = DomCandidateExtractor.extract(html, List.of("data-axis-test-id"));
        Assert.assertTrue(list.stream().anyMatch(c ->
                        "css".equals(c.strategy())
                                && c.value().contains("data-axis-test-id")
                                && c.value().contains("verify_Otp_Button")),
                DomCandidateExtractor.formatTable(list));
        Assert.assertTrue(list.stream().noneMatch(c -> "id".equals(c.strategy()) && "x".equals(c.value())),
                "preferred hook must suppress the same-node id: "
                        + DomCandidateExtractor.formatTable(list));
        Assert.assertTrue(list.stream().noneMatch(c ->
                        c.value() != null && c.value().contains("Verify OTP") && "xpath".equals(c.strategy())),
                "preferred hook must skip text xpath: " + DomCandidateExtractor.formatTable(list));
        Assert.assertEquals(
                DomCandidateExtractor.strategyRank("css", "[data-axis-test-id='verify_Otp_Button']",
                        List.of("data-axis-test-id")),
                45);
        StepIntentBinder.BindResult bound = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Click the Verify OTP button"),
                "TC1", list, List.of());
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(bound.steps().get(0).locatorValue().contains("data-axis-test-id"),
                bound.steps().get(0).locatorValue());
    }

    @Test
    public void extractsVendorDataTestHookAsCss() {
        String html = """
                <body>
                  <button data-axis-test-id="verify_Otp_Button">Verify OTP</button>
                </body>
                """;
        List<DomCandidate> list = DomCandidateExtractor.extract(html);
        Assert.assertTrue(list.stream().anyMatch(c ->
                        "css".equals(c.strategy())
                                && c.value().contains("data-axis-test-id")
                                && c.value().contains("verify_Otp_Button")),
                DomCandidateExtractor.formatTable(list));
    }

    @Test
    public void extractsSidebarSubmenuByVisibleText() {
        String html = """
                <body>
                  <div class="ant-menu">
                    <div aria-expanded="false"><span>Dashboard</span></div>
                    <div aria-expanded="false"><span>Wallets</span></div>
                    <div aria-expanded="false"><span>User Management</span></div>
                  </div>
                </body>
                """;
        List<DomCandidate> list = DomCandidateExtractor.extract(html);
        Assert.assertTrue(list.stream().anyMatch(c ->
                        c.label() != null && c.label().contains("User Management")
                                && DomCandidateExtractor.isBindableStrategy(c.strategy())),
                DomCandidateExtractor.formatTable(list));
        StepIntentBinder.BindResult bound = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK,
                        "Click User Management in the left sidebar (expand if collapsed)"),
                "TC_06", list, List.of());
        Assert.assertTrue(bound.ok(), bound.rejectReason() + "\n" + DomCandidateExtractor.formatTable(list));
        Assert.assertTrue(bound.steps().get(0).locatorValue().contains("User Management"),
                bound.steps().get(0).locatorValue());
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
