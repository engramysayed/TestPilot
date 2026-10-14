package delivery.vision;

import delivery.authoring.DomCandidate;
import delivery.store.PreferredHooksStore;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.*;

/** Real browser identity checks, without AI or customer pages. */
public class HealthVisionGroundingTest {
    @Test public void sidebarAndPreferredLocatorShareTheObservedNode() throws Exception {
        ChromeOptions options=new ChromeOptions();options.addArguments("--headless=new","--window-size=1000,700");
        WebDriver driver=new ChromeDriver(options);
        try (var hooks=PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            driver.get("data:text/html,"+java.net.URLEncoder.encode("<aside><ul><li id='fallback' data-axis-test-id='support_Menu' onclick='void(0)' style='width:150px;height:50px'>Support</li></ul></aside>",java.nio.charset.StandardCharsets.UTF_8).replace("+","%20"));
            WebElement element=driver.findElement(By.id("fallback"));
            var r=element.getRect();var browser=new SeleniumGroundingBrowser(driver);
            var visual=new VisualCandidate("Support",new BoundingBox(r.x,r.y,r.width,r.height),0.9);
            var table=List.of(new DomCandidate("stable","id","fallback","menuitem","Support"),
                    new DomCandidate("preferred","css","li[data-axis-test-id='support_Menu']","menuitem","Support"));
            var hit=ElementGrounder.groundToHit(table,visual,browser,1000,700,1000,700).orElseThrow();
            Assert.assertEquals(hit.candidateId(),"preferred");
            var fresh=ElementGrounder.groundToHit(List.of(),visual,browser,1000,700,1000,700).orElseThrow();
            Assert.assertEquals(fresh.table().get(0).value(),"li[data-axis-test-id='support_Menu']");
            ((JavascriptExecutor)driver).executeScript("arguments[0].setAttribute('aria-disabled','true')",element);
            Assert.assertTrue(ElementGrounder.groundToHit(table,visual,browser,1000,700,1000,700).isEmpty());
        } finally { driver.quit(); }
    }
    @Test public void duplicateHooksFallBackToUniqueObservedId() throws Exception {
        ChromeOptions options=new ChromeOptions();options.addArguments("--headless=new","--window-size=1000,700");
        WebDriver driver=new ChromeDriver(options);
        try (var hooks=PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            driver.get("data:text/html,<button id='first' data-axis-test-id='duplicate'>First</button><button id='second' data-axis-test-id='duplicate'>Second</button>");
            var r=driver.findElement(By.id("second")).getRect();
            var table=List.of(new DomCandidate("hook","css","button[data-axis-test-id='duplicate']","button","Second"),
                    new DomCandidate("id","id","second","button","Second"));
            var hit=ElementGrounder.groundToHit(table,new VisualCandidate("Second",new BoundingBox(r.x,r.y,r.width,r.height),0.9),
                    new SeleniumGroundingBrowser(driver),1000,700,1000,700).orElseThrow();
            Assert.assertEquals(hit.candidateId(),"id");
        } finally { driver.quit(); }
    }
}
