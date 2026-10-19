package delivery.vision;

import delivery.authoring.NestedPreferredHook;
import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class NestedHookVisionGrounderTest {
    @Test
    public void buildLocator_usesPreferredAxisHook() {
        GroundedNode node = new GroundedNode(
                "a", "", "", "", "", "link", true, true, "a",
                "Bill Payment Settlement", "data-testid",
                "data-axis-test-id", "side-nav-bill-payment-settlement-link");
        Optional<delivery.authoring.LocatorCandidate> loc = ElementGrounder.buildLocator(node);
        Assert.assertTrue(loc.isPresent());
        Assert.assertEquals(loc.get().strategy(), "css");
        Assert.assertTrue(loc.get().value().contains("data-axis-test-id"));
        Assert.assertTrue(loc.get().value().contains("side-nav-bill-payment-settlement-link"));
    }

    @Test
    public void rewriteAfterVisionXpath_usesChildHook() {
        String html = """
                <html><body><aside class="sidebar"><li role="menuitem">
                <a data-axis-test-id="side-nav-bill-payment-settlement-link">Bill Payment Settlement</a>
                </li></aside></body></html>
                """;
        ProvenStep vision = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK [catalog xpath]");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(vision, html);
            Assert.assertEquals(out.locatorStrategy(), "css");
            Assert.assertTrue(out.locatorValue().contains("data-axis-test-id"));
            Assert.assertTrue(out.locatorValue().contains("side-nav-bill-payment-settlement-link"));
        }
    }

    @Test
    public void elementFromPoint_ignoresGeneratedUniqueHook() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1000,700");
        WebDriver driver = new ChromeDriver(options);
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id", "data-testid"))) {
            driver.get("data:text/html,<button id=\"btn\" data-testid=\"_r_15_\" style=\"width:120px;height:40px\">Go</button>");
            WebElement btn = driver.findElement(By.id("btn"));
            var rect = btn.getRect();
            double x = rect.x + rect.width / 2.0;
            double y = rect.y + rect.height / 2.0;
            GroundedNode node = new SeleniumGroundingBrowser(driver).elementFromPoint(x, y);
            Assert.assertNotNull(node);
            Assert.assertTrue(node.preferredAttribute().isBlank());
            Assert.assertTrue(node.preferredValue().isBlank());
        } finally {
            driver.quit();
        }
    }

    @Test
    public void elementFromPoint_ownHookWinsWhenChildAlsoHasOne() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1000,700");
        WebDriver driver = new ChromeDriver(options);
        try (var ignored = PreferredHooksStore.activate(List.of("data-testid"))) {
            driver.get("data:text/html,"
                    + "<button id=\"btn\" data-testid=\"own-hook\" "
                    + "style=\"position:absolute;left:10px;top:10px;width:180px;height:80px;padding:0\">"
                    + "<span data-testid=\"child-hook\" "
                    + "style=\"position:absolute;left:0;bottom:0;width:40px;height:16px\">Go</span>"
                    + "</button>");
            WebElement btn = driver.findElement(By.id("btn"));
            var rect = btn.getRect();
            double x = rect.x + 8;
            double y = rect.y + 8;
            GroundedNode node = new SeleniumGroundingBrowser(driver).elementFromPoint(x, y);
            Assert.assertNotNull(node);
            Assert.assertEquals(node.tag(), "button");
            Assert.assertEquals(node.preferredAttribute(), "data-testid");
            Assert.assertEquals(node.preferredValue(), "own-hook");
        } finally {
            driver.quit();
        }
    }

    @Test
    public void elementFromPoint_twoUniqueHooksOnNode_leavesPreferredEmpty() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1000,700");
        WebDriver driver = new ChromeDriver(options);
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id", "data-testid"))) {
            driver.get("data:text/html,<button id=\"btn\" data-testid=\"hook-a\" data-axis-test-id=\"hook-b\" style=\"width:120px;height:40px\">Go</button>");
            WebElement btn = driver.findElement(By.id("btn"));
            var rect = btn.getRect();
            double x = rect.x + rect.width / 2.0;
            double y = rect.y + rect.height / 2.0;
            GroundedNode node = new SeleniumGroundingBrowser(driver).elementFromPoint(x, y);
            Assert.assertNotNull(node);
            Assert.assertTrue(node.preferredAttribute().isBlank());
            Assert.assertTrue(node.preferredValue().isBlank());
        } finally {
            driver.quit();
        }
    }
}
