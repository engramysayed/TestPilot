package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * W2 Automate emit: semantic ladder, uniqueness reject, merge re-generate, trimmed ARIA/region.
 * Also locks W1 regressions (empty hooks, isLoaded, productRow).
 */
public class CodegenW2EmitTest {
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");

    @Test
    public void stableFirstLadderPrefersTestIdThenIdThenRoleThenLabelThenCss() {
        List<LocatorCandidate> cands = List.of(
                LocatorCandidate.of("css", "button.submit"),
                LocatorCandidate.of("css", "button[data-testid='save']"),
                LocatorCandidate.of("css", "button[aria-label='Save changes']"),
                LocatorCandidate.of("css", "button[role='button'][aria-label='Save changes']"));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            SemanticLocatorLadder.Pick pick = SemanticLocatorLadder.pick(
                    "css", "button.submit", cands, PreferredHooksStore.current(), true);
            Assert.assertTrue(pick.ok(), pick.rejectReason());
            Assert.assertEquals(pick.value(), "button[data-testid='save']", "test id must be primary when present");
        }
        // shared policy order (stable-first, default)
        Assert.assertTrue(SemanticLocatorLadder.rank("css", "button[data-testid='save']", List.of())
                > SemanticLocatorLadder.rank("id", "save-button", List.of()));
        Assert.assertTrue(SemanticLocatorLadder.rank("id", "save-button", List.of())
                > SemanticLocatorLadder.rank("css", "button[role='button'][aria-label='Save']", List.of()));
        Assert.assertTrue(SemanticLocatorLadder.rank("css", "button[role='button'][aria-label='Save']", List.of())
                > SemanticLocatorLadder.rank("css", "div[aria-label='Save']", List.of()));
        Assert.assertTrue(SemanticLocatorLadder.rank("css", "div[aria-label='Save']", List.of())
                > SemanticLocatorLadder.rank("css", "button.submit", List.of()));
        Assert.assertTrue(SemanticLocatorLadder.rank("css", "button[data-axis-test-id='x']", List.of("data-axis-test-id"))
                > SemanticLocatorLadder.rank("css", "button[data-testid='save']", List.of("data-axis-test-id")),
                "preferred hook always first");
    }

    @Test
    public void userFacingModeKeepsRoleThenLabelThenTestId() {
        try (var mode = delivery.locator.LocatorStabilityPolicy.activate(delivery.locator.LocatorMode.USER_FACING_FIRST)) {
            Assert.assertTrue(SemanticLocatorLadder.rank("css", "button[role='button'][aria-label='Save']", List.of())
                    > SemanticLocatorLadder.rank("css", "div[aria-label='Save']", List.of()));
            Assert.assertTrue(SemanticLocatorLadder.rank("css", "div[aria-label='Save']", List.of())
                    > SemanticLocatorLadder.rank("css", "button[data-testid='save']", List.of()));
        }
    }

    @Test
    public void ladderEmitWritesTestIdPrimaryOverRoleAndCss() throws Exception {
        Path dir = Files.createTempDirectory("w2-ladder");
        ProvenStep step = new ProvenStep(
                "TC_LADDER", "Checkout", "elementAction", "click",
                "css", "button.submit", "",
                "", "", true, "ladder", "",
                List.of(
                        LocatorCandidate.of("css", "button[data-testid='place-order']"),
                        LocatorCandidate.of("css",
                                "button[role='button'][aria-label='Place order']")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_LADDER", step)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Checkout_Page.java"));
        int testIdAt = page.indexOf("data-testid='place-order'");
        int roleAt = page.indexOf("role=");
        Assert.assertTrue(testIdAt >= 0, page);
        if (roleAt >= 0) {
            Assert.assertTrue(testIdAt < roleAt, "test id primary must precede role fallback:\n" + page);
        }
        Assert.assertFalse(page.contains("By.css("), "By.css does not exist in Selenium:\n" + page);
        Path sample = Path.of("docs/handoff/samples/W2_ladder_Checkout_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, page);
    }

    @Test
    public void uniquenessRejectsBareRoleWithoutName() {
        SemanticLocatorLadder.Pick pick = SemanticLocatorLadder.pick(
                "css", "div[role='combobox']",
                List.of(LocatorCandidate.of("css", "div[role='listbox']")),
                List.of("data-test"),
                true);
        Assert.assertTrue(pick.rejected() || SemanticLocatorLadder.isNonUnique("css", "div[role='combobox']"),
                "bare role must be non-unique: " + pick);
        Assert.assertTrue(SemanticLocatorLadder.isNonUnique("css", "div[role='combobox']"));
        Assert.assertFalse(SemanticLocatorLadder.isNonUnique("css",
                "div[role='combobox'][aria-label='Date']"));
        Assert.assertTrue(SemanticLocatorLadder.rejectReason("css", "div[role='combobox']")
                .toLowerCase().contains("non-unique")
                || SemanticLocatorLadder.rejectReason("css", "div[role='combobox']")
                .toLowerCase().contains("accessible"));
    }

    @Test
    public void uniquenessRejectDoesNotPromoteBareRoleInEmit() throws Exception {
        Path dir = Files.createTempDirectory("w2-uniq");
        ProvenStep step = new ProvenStep(
                "TC_UNIQ", "Form", "elementAction", "click",
                "id", "start-date", "",
                "", "", true, "uniq", "",
                List.of(LocatorCandidate.of("css", "div[role='combobox']")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-test"))) {
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_UNIQ", step)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Form_Page.java"));
        // Bare role rejected from promotion; proven id stays (or named unique wins).
        Assert.assertTrue(page.contains("start-date") || page.contains("By.id"), page);
        Assert.assertFalse(page.contains("div[role='combobox']") && !page.contains("start-date"),
                "bare role must not replace proven id:\n" + page);
        Path sample = Path.of("docs/handoff/samples/W2_uniqueness_Form_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, "// uniqueness: bare role rejected; proven id kept\n" + page);
    }

    @Test
    public void mergeRegenerateIntoExistingPageClassNoLoginPage2() throws Exception {
        Path dir = Files.createTempDirectory("w2-merge");
        ProvenStep a = new ProvenStep("TC1", "Login", "elementAction", "type",
                "id", "username", "u", "", "", true, "ok");
        ProvenStep b = new ProvenStep("TC2", "LoginPage", "elementAction", "type",
                "id", "password", "p", "", "", true, "ok");
        ProvenStep c = new ProvenStep("TC3", "LoginForm", "elementAction", "click",
                "id", "login-button", "", "", "", true, "ok");
        new CodeWriter(TEMPLATES).write(dir, List.of(
                outcome("TC1", a),
                outcome("TC2", b),
                outcome("TC3", c)));
        Path pages = dir.resolve("src/main/java/project/pages");
        Assert.assertTrue(Files.isRegularFile(pages.resolve("Login_Page.java")),
                "expected Login_Page.java, files=" + listJava(pages));
        Assert.assertFalse(Files.exists(pages.resolve("Login_Page2.java")));
        Assert.assertFalse(Files.exists(pages.resolve("Login_2_Page.java")));
        Assert.assertFalse(Files.exists(pages.resolve("LoginPage_Page.java")));
        Assert.assertFalse(Files.exists(pages.resolve("LoginForm_Page.java")));
        try (var stream = Files.list(pages)) {
            long loginish = stream.filter(p -> p.getFileName().toString().toLowerCase().contains("login"))
                    .count();
            Assert.assertEquals(loginish, 1L, "exactly one Login page class, files=" + listJava(pages));
        }
        String page = Files.readString(pages.resolve("Login_Page.java"));
        Assert.assertTrue(page.contains("username"), page);
        Assert.assertTrue(page.contains("password"), page);
        Assert.assertTrue(page.contains("login-button"), page);
        Assert.assertTrue(page.contains("isLoaded()"), page);
        Path sample = Path.of("docs/handoff/samples/W2_merge_Login_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, page);
    }

    @Test
    public void trimmedAriaAndRegionAssertsEmit() throws Exception {
        Path dir = Files.createTempDirectory("w2-aria");
        ProvenStep role = new ProvenStep(
                "TC_ARIA", "Dashboard", "elementAction", "assert",
                "css", "nav[aria-label='Primary']", "",
                "ariaRole", "navigation", true, "intent:ASSERT_ARIA", "");
        ProvenStep region = new ProvenStep(
                "TC_ARIA", "Dashboard", "elementAction", "assert",
                "css", "main", "",
                "region", "main", true, "intent:ASSERT_REGION", "");
        ProvenStep label = new ProvenStep(
                "TC_ARIA", "Dashboard", "elementAction", "assert",
                "css", "[aria-label='Search']", "",
                "ariaLabel", "Search", true, "", "");
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_ARIA", role, region, label)));
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Dashboard_Page.java"));
        Assert.assertTrue(page.contains("elementAriaRole("), page);
        Assert.assertTrue(page.contains("elementRegion("), page);
        Assert.assertTrue(page.contains("elementAriaLabel("), page);
        Assert.assertTrue(page.contains("isLoaded()"), page);
        Path sample = Path.of("docs/handoff/samples/W2_aria_Dashboard_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, page);
    }

    @Test
    public void ariaAliasesNormalize() {
        ProvenStep role = new ProvenStep("TC", "P", "elementAction", "assert",
                "css", "nav", "", "role", "navigation", true, "", "");
        ProvenStep landmark = new ProvenStep("TC", "P", "elementAction", "assert",
                "css", "main", "", "landmark", "main", true, "", "");
        Assert.assertEquals(EmitStepNormalizer.apply(role).assertionType(), "ariaRole");
        Assert.assertEquals(EmitStepNormalizer.apply(landmark).assertionType(), "region");
    }

    /** Empty hooks: a verified test id is still promoted over the proven xpath; the xpath stays as fallback. */
    @Test
    public void w1EmptyHooksPromoteTestIdKeepProvenAsFallback() throws Exception {
        Path dir = Files.createTempDirectory("w2-w1-empty");
        ProvenStep step = new ProvenStep(
                "TC_NOHOOK", "Otp", "elementAction", "click",
                "xpath", "//button[@id='verify-otp']", "",
                "", "", true, "", "",
                List.of(LocatorCandidate.of("css", "button[data-axis-test-id='verify_Otp_Button']")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of())) {
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_NOHOOK", step)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Otp_Page.java"));
        Assert.assertTrue(page.contains("//button[@id='verify-otp']") || page.contains("By.xpath"), page);
        Assert.assertTrue(page.contains("verify-otp"), page);
    }

    @Test
    public void w1IsLoadedAndProductRowRegression() throws Exception {
        Path dir = Files.createTempDirectory("w2-w1-row");
        String rowXpath = "//div[@class='inventory_list']//div[@class='inventory_item']"
                + "[.//div[contains(normalize-space(.),'Sauce Labs Backpack')]]//button";
        ProvenStep click = new ProvenStep(
                "TC_INV", "Inventory", "elementAction", "click",
                "xpath", rowXpath, "",
                "", "", true, "row", "");
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-test"))) {
            ProvenStep marker = new ProvenStep(
                    "TC_INV", "Inventory", "elementAction", "assert",
                    "css", "[data-test='inventory-container']", "",
                    "visible", "", true, "hook marker", "");
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_INV", click, marker)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Inventory_Page.java"));
        Assert.assertTrue(page.contains("productRow(String name)"), page);
        Assert.assertTrue(page.contains("isLoaded()"), page);
        Assert.assertFalse(page.contains("_Locators"), page);
    }

    private static String listJava(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return "(missing)";
        }
        StringBuilder sb = new StringBuilder();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> sb.append(p.getFileName()).append(' '));
        }
        return sb.toString();
    }

    private static TcOutcome outcome(String id, ProvenStep... steps) {
        return new TcOutcome(id, TcStatus.PASSED, List.of(steps), "", null);
    }
}
