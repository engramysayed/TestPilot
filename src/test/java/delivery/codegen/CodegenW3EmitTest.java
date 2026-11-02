package delivery.codegen;

import delivery.heal.HealProposal;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.portal.service.HealJavaLocatorPatcher;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * W3 Automate emit: multi-locator bag, DOM-diff asserts, heal Accept existing-only, NL→By cache.
 * Also locks W1/W2 regressions (empty hooks, ladder, merge, isLoaded/productRow).
 */
public class CodegenW3EmitTest {
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");

    @Test
    public void multiLocatorBagPrimaryThenOrderedFallbacks() throws Exception {
        Path dir = Files.createTempDirectory("w3-bag");
        ProvenStep step = new ProvenStep(
                "TC_BAG", "Checkout", "elementAction", "click",
                "css", "button.submit", "",
                "", "", true, "bag", "",
                List.of(
                        LocatorCandidate.of("css", "button[data-testid='place-order']"),
                        LocatorCandidate.of("css",
                                "button[role='button'][aria-label='Place order']")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            MultiLocatorBag.Bag bag = MultiLocatorBag.build(step);
            Assert.assertTrue(bag.primary().value().contains("role=")
                    || bag.primary().value().contains("aria-label"), bag.primary().value());
            Assert.assertTrue(bag.hasFallbacks(), "expected fallbacks: " + bag);
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_BAG", step)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Checkout_Page.java"));
        Assert.assertTrue(page.contains("role=") || page.contains("Place order"), page);
        // Bag fallbacks as _fbN and/or KeelBy targets
        boolean hasFb = page.contains("_fb1") || page.contains("KeelBy.of") || page.contains("button.submit")
                || page.contains("data-testid");
        Assert.assertTrue(hasFb, "expected bag fallbacks in emit:\n" + page);
        int roleAt = Math.max(page.indexOf("role="), page.indexOf("aria-label"));
        int bareCss = page.indexOf("button.submit");
        if (roleAt >= 0 && bareCss >= 0) {
            Assert.assertTrue(roleAt < bareCss, "primary must precede bare css:\n" + page);
        }
        Path sample = Path.of("docs/handoff/samples/W3_bag_Checkout_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, page);
    }

    @Test
    public void emptyHooksBagKeepsProvenPrimaryAddsFallbacks() throws Exception {
        Path dir = Files.createTempDirectory("w3-bag-empty");
        ProvenStep step = new ProvenStep(
                "TC_BAG0", "Otp", "elementAction", "click",
                "xpath", "//button[@id='verify-otp']", "",
                "", "", true, "", "",
                List.of(LocatorCandidate.of("css", "button[data-axis-test-id='verify_Otp_Button']")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of())) {
            MultiLocatorBag.Bag bag = MultiLocatorBag.build(step);
            Assert.assertTrue(bag.primary().value().contains("verify-otp"), bag.primary().toString());
            Assert.assertTrue(bag.hasFallbacks(), bag.toString());
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_BAG0", step)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Otp_Page.java"));
        Assert.assertTrue(page.contains("//button[@id='verify-otp']") || page.contains("By.xpath"), page);
        Assert.assertTrue(page.contains("_fb1") || page.contains("data-axis-test-id"), page);
    }

    @Test
    public void domDiffSuggestsTrimmedAssertsIntoEmit() throws Exception {
        Path dir = Files.createTempDirectory("w3-domdiff");
        PageAccumulator acc = new PageAccumulator();
        ProvenStep base = new ProvenStep(
                "TC_DD", "Cart", "elementAction", "click",
                "id", "checkout", "", "", "", true, "base");
        acc.add(base);
        List<DomDiffAssertSuggestor.DiffHint> hints = List.of(
                new DomDiffAssertSuggestor.DiffHint(
                        "added", "css", "[data-test='complete-header']", "", "", "banner appeared"),
                new DomDiffAssertSuggestor.DiffHint(
                        "text", "css", ".summary_total_label", "textContains", "Total: $", "total text"),
                new DomDiffAssertSuggestor.DiffHint(
                        "removed", "id", "spinner", "", "", "spinner gone"),
                new DomDiffAssertSuggestor.DiffHint(
                        "noise", "css", "div", "customWeird", "x", "should drop"));
        List<DomDiffAssertSuggestor.Suggestion> suggestions = DomDiffAssertSuggestor.suggest(hints);
        Assert.assertTrue(suggestions.size() <= DomDiffAssertSuggestor.MAX_SUGGESTIONS, "" + suggestions.size());
        Assert.assertTrue(suggestions.stream().anyMatch(s -> "visible".equals(s.assertionType())));
        Assert.assertTrue(suggestions.stream().anyMatch(s -> "textContains".equals(s.assertionType())));
        Assert.assertTrue(suggestions.stream().anyMatch(s -> "notVisible".equals(s.assertionType())));
        Assert.assertTrue(suggestions.stream().noneMatch(s -> "customWeird".equals(s.assertionType())));
        acc.addDomDiffSuggestions("TC_DD", "Cart", hints);
        // Emit via CodeWriter from outcomes that include suggested steps
        List<ProvenStep> steps = new java.util.ArrayList<>();
        steps.add(base);
        for (DomDiffAssertSuggestor.Suggestion s : suggestions) {
            steps.add(s.toStep("TC_DD", "Cart"));
        }
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_DD", steps.toArray(ProvenStep[]::new))));
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Cart_Page.java"));
        Assert.assertTrue(page.contains("elementVisable(") || page.contains("complete-header"), page);
        Assert.assertTrue(page.contains("textContains(") || page.contains("Total"), page);
        Assert.assertTrue(page.contains("elementNotVisible(") || page.contains("spinner"), page);
        Assert.assertTrue(page.contains("isLoaded()"), page);
        Path sample = Path.of("docs/handoff/samples/W3_domdiff_Cart_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, page);
    }

    @Test
    public void healAcceptPatchesExistingPageOnlyRejectsLoginPage2() throws Exception {
        Path dir = Files.createTempDirectory("w3-heal");
        ProvenStep a = new ProvenStep("TC1", "Login", "elementAction", "type",
                "id", "user-name", "u", "", "", true, "ok");
        ProvenStep b = new ProvenStep("TC1", "Login", "elementAction", "type",
                "id", "password", "p", "", "", true, "ok");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC1", a, b)));
        Path pageFile = dir.resolve("src/main/java/project/pages/Login_Page.java");
        Assert.assertTrue(Files.isRegularFile(pageFile));
        String before = Files.readString(pageFile);
        Assert.assertTrue(before.contains("user-name"), before);

        Assert.assertTrue(HealAcceptPatcher.looksInvented("LoginPage2"));
        Assert.assertTrue(HealAcceptPatcher.looksInvented("Login_Page2"));
        Assert.assertFalse(HealAcceptPatcher.looksInvented("Login_Page"));
        Assert.assertEquals(HealAcceptPatcher.alignPageClass("LoginPage"), "Login_Page");
        Assert.assertEquals(HealAcceptPatcher.alignPageClass("LoginForm"), "Login_Page");

        Map<String, Object> shell = HealAcceptPatcher.proposalShell(
                "prop-1", "proj", "job", "TC1", 0, "LoginPage",
                "id", "user-name", "css", "[data-test='username']", "heal username");
        Assert.assertEquals(shell.get("pageClass"), "Login_Page");
        Assert.assertEquals(shell.get("status"), "pending");
        Assert.assertEquals(shell.get("patchKind"), "locator");

        HealProposal proposal = new HealProposal(
                "prop-1", "proj", "job", "TC1", 0, "Login_Page",
                new HealProposal.LocatorRef("id", "user-name"),
                new HealProposal.LocatorRef("cssSelector", "[data-test='username']"),
                "locator", "t1", "heal", "pending", null, null, null);
        HealJavaLocatorPatcher.PatchOutcome out = HealAcceptPatcher.accept(dir, proposal);
        Assert.assertTrue(out.changed(), out.detail());
        String after = Files.readString(pageFile);
        Assert.assertTrue(after.contains("[data-test='username']") || after.contains("data-test"), after);
        Assert.assertFalse(Files.exists(dir.resolve("src/main/java/project/pages/Login_Page2.java")));
        Assert.assertFalse(Files.exists(dir.resolve("src/main/java/project/pages/LoginPage2.java")));

        try {
            HealAcceptPatcher.accept(dir, new HealProposal(
                    "bad", "proj", "job", "TC1", 0, "LoginPage2",
                    new HealProposal.LocatorRef("id", "password"),
                    new HealProposal.LocatorRef("id", "password2"),
                    "locator", "", "", "pending", null, null, null));
            Assert.fail("must refuse LoginPage2");
        } catch (HealJavaLocatorPatcher.BadPatchException | HealJavaLocatorPatcher.MissingPageException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("login")
                    || expected.getMessage().contains("2")
                    || expected.getMessage().contains("invent"), expected.getMessage());
        }

        // Missing page → 409-style MissingPageException (do not create)
        Path empty = Files.createTempDirectory("w3-heal-miss");
        Files.createDirectories(empty.resolve("src/main/java/project/pages"));
        try {
            HealAcceptPatcher.accept(empty, new HealProposal(
                    "miss", "proj", "job", "TC1", 0, "Login_Page",
                    new HealProposal.LocatorRef("id", "x"),
                    new HealProposal.LocatorRef("id", "y"),
                    "locator", "", "", "pending", null, null, null));
            Assert.fail("missing page must 409");
        } catch (HealJavaLocatorPatcher.MissingPageException expected) {
            Assert.assertTrue(expected.getMessage().contains("409")
                    || expected.getMessage().toLowerCase().contains("not found"), expected.getMessage());
        }

        Path sample = Path.of("docs/handoff/samples/W3_heal_accept_Login_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, after);
    }

    @Test
    public void nlStepCachesValidatedByOnPageClass() throws Exception {
        Path dir = Files.createTempDirectory("w3-nl");
        ProvenStep click = new ProvenStep(
                "TC_NL", "Login", "elementAction", "click",
                "id", "login-button", "", "", "", true, "ok");
        var cached = NlLocatorCache.resolve("Login", "click the login button", "id", "login-button");
        Assert.assertTrue(cached.isPresent());
        Assert.assertTrue(cached.get().fieldName().startsWith("nl_"));
        Assert.assertTrue(cached.get().fieldName().contains("login"));
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_NL", click)), List.of(cached.get()));
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Login_Page.java"));
        Assert.assertTrue(page.contains("nl_") && page.contains("login"), page);
        Assert.assertTrue(page.contains("login-button"), page);
        Assert.assertTrue(page.contains("NL-cached") || page.contains("nl_click"), page);
        Assert.assertFalse(page.contains("Login_Page2"));
        Path sample = Path.of("docs/handoff/samples/W3_nl_Login_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, page);
    }

    @Test
    public void w1EmptyHooksAndW2MergeRegression() throws Exception {
        Path dir = Files.createTempDirectory("w3-reg");
        ProvenStep empty = new ProvenStep(
                "TC_NOHOOK", "Otp", "elementAction", "click",
                "xpath", "//button[@id='verify-otp']", "",
                "", "", true, "", "",
                List.of(LocatorCandidate.of("css", "button[data-axis-test-id='verify_Otp_Button']")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of())) {
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_NOHOOK", empty)));
        }
        String otp = Files.readString(dir.resolve("src/main/java/project/pages/Otp_Page.java"));
        Assert.assertTrue(otp.contains("verify-otp"), otp);

        Path dir2 = Files.createTempDirectory("w3-merge");
        ProvenStep a = new ProvenStep("TC1", "Login", "elementAction", "type",
                "id", "username", "u", "", "", true, "ok");
        ProvenStep b = new ProvenStep("TC2", "LoginPage", "elementAction", "type",
                "id", "password", "p", "", "", true, "ok");
        new CodeWriter(TEMPLATES).write(dir2, List.of(outcome("TC1", a), outcome("TC2", b)));
        Path pages = dir2.resolve("src/main/java/project/pages");
        Assert.assertTrue(Files.isRegularFile(pages.resolve("Login_Page.java")));
        Assert.assertFalse(Files.exists(pages.resolve("Login_Page2.java")));
    }

    private static TcOutcome outcome(String id, ProvenStep... steps) {
        return new TcOutcome(id, TcStatus.PASSED, List.of(steps), "", null);
    }
}
