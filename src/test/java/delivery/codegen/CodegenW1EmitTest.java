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
 * W1 Automate emit: preferred-hook-first, assert-from-element, isLoaded, parameterized rows.
 */
public class CodegenW1EmitTest {
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");

    @Test
    public void preferredHookBecomesPrimaryOverIdAndXpath() throws Exception {
        Path dir = Files.createTempDirectory("w1-hooks");
        ProvenStep step = new ProvenStep(
                "TC_HOOK", "Otp", "elementAction", "click",
                "id", "verify-otp", "",
                "", "", true, "preferred", "",
                List.of(LocatorCandidate.of("css", "button[data-axis-test-id='verify_Otp_Button']"),
                        LocatorCandidate.of("xpath",
                                "//button[contains(normalize-space(.),'Verify OTP')]")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_HOOK", step)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Otp_Page.java"));
        Assert.assertTrue(page.contains("data-axis-test-id"), page);
        int hookAt = page.indexOf("data-axis-test-id");
        int idAt = page.indexOf("verify-otp");
        Assert.assertTrue(hookAt >= 0, page);
        if (idAt >= 0) {
            Assert.assertTrue(hookAt < idAt, "hook must precede id fallback:\n" + page);
        }
        Assert.assertFalse(page.contains("Verify OTP"), "text xpath must not remain as fallback:\n" + page);
        Assert.assertTrue(page.contains("isLoaded()"), page);
        Assert.assertFalse(page.contains("_Locators"), page);
        Assert.assertFalse(page.contains("_Actions"), page);
    }

    @Test
    public void emptyHooksKeepProvenPrimary() throws Exception {
        Path dir = Files.createTempDirectory("w1-nohooks");
        ProvenStep step = new ProvenStep(
                "TC_NOHOOK", "Otp", "elementAction", "click",
                "xpath", "//button[@id='verify-otp']", "",
                "", "", true, "", "",
                List.of(LocatorCandidate.of("css", "button[data-axis-test-id='verify_Otp_Button']")));
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of())) {
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_NOHOOK", step)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Otp_Page.java"));
        // Empty hooks: proven xpath primary must remain (candidate ladder must not promote css).
        Assert.assertTrue(page.contains("//button[@id='verify-otp']") || page.contains("By.xpath"), page);
        Assert.assertTrue(page.contains("verify-otp"), page);
    }

    @Test
    public void parameterizedProductRowAndValueAssertAndIsLoaded() throws Exception {
        Path dir = Files.createTempDirectory("w1-row");
        String rowXpath = "//div[@class='inventory_list']//div[@class='inventory_item']"
                + "[.//div[contains(normalize-space(.),'Sauce Labs Backpack')]]//button";
        ProvenStep click = new ProvenStep(
                "TC_INV", "Inventory", "elementAction", "click",
                "xpath", rowXpath, "",
                "", "", true, "row", "");
        ProvenStep value = new ProvenStep(
                "TC_INV", "Inventory", "elementAction", "assert",
                "xpath",
                "//div[@class='inventory_item'][.//div[contains(normalize-space(.),'Sauce Labs Backpack')]]"
                        + "//div[@class='inventory_item_price']",
                "",
                "value", "$29.99", true,
                "intent:ASSERT_VISIBLE:value field=price", "");
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-test"))) {
            ProvenStep marker = new ProvenStep(
                    "TC_INV", "Inventory", "elementAction", "assert",
                    "css", "[data-test='inventory-container']", "",
                    "visible", "", true, "hook marker", "");
            new CodeWriter(TEMPLATES).write(dir, List.of(outcome("TC_INV", click, value, marker)));
        }
        String page = Files.readString(dir.resolve("src/main/java/project/pages/Inventory_Page.java"));
        Assert.assertTrue(page.contains("productRow(String name)"), page);
        Assert.assertTrue(page.contains("productRow(\"Sauce Labs Backpack\")"), page);
        Assert.assertTrue(page.contains("isLoaded()"), page);
        Assert.assertTrue(page.contains("elementValue(") || page.contains("assert_") && page.contains("_Value"),
                page);
        Assert.assertTrue(page.contains("data-test") || page.contains("inventory-container"), page);
        int fieldsIdx = page.indexOf("productRow(String name)");
        int actionIdx = page.indexOf("public void click_");
        int loadedIdx = page.indexOf("isLoaded()");
        Assert.assertTrue(fieldsIdx >= 0 && actionIdx > fieldsIdx, page);
        Assert.assertTrue(loadedIdx > actionIdx, "isLoaded after actions:\n" + page);
        Assert.assertFalse(page.contains("class Inventory_Locators"), page);
        Assert.assertFalse(page.contains("class Inventory_Actions"), page);

        // Persist sample evidence for handoff.
        Path sample = Path.of("docs/handoff/samples/W1_Inventory_Page.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, page);
    }

    @Test
    public void assertFromElementAliasesNormalize() {
        ProvenStep text = new ProvenStep("TC", "P", "elementAction", "assert",
                "id", "x", "", "text", "Hello", true, "", "");
        ProvenStep valueEq = new ProvenStep("TC", "P", "elementAction", "assert",
                "id", "x", "", "valueEquals", "1", true, "", "");
        ProvenStep intent = new ProvenStep("TC", "P", "elementAction", "assert",
                "id", "x", "", "textContains", "1", true,
                "intent:ASSERT_VISIBLE:value", "");
        Assert.assertEquals(EmitStepNormalizer.apply(text).assertionType(), "textContains");
        Assert.assertEquals(EmitStepNormalizer.apply(valueEq).assertionType(), "value");
        Assert.assertEquals(EmitStepNormalizer.apply(intent).assertionType(), "value");
    }

    private static TcOutcome outcome(String id, ProvenStep... steps) {
        return new TcOutcome(id, TcStatus.PASSED, List.of(steps), "", null);
    }
}
