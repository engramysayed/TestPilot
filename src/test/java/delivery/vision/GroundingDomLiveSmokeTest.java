package delivery.vision;

import delivery.authoring.DomCandidateExtractor;
import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;
import java.util.List;

/** Real headless browser, local in-memory page, deterministic coordinates; no external site or model. */
public class GroundingDomLiveSmokeTest {
    @Test
    public void browserResolvesOriginalControlAndGroundedIdentity() throws Exception {
        String old = System.getProperty("EXECUTION_TYPE");
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        WebDriverFactory factory = new WebDriverFactory();
        try {
            WebDriver driver = factory.get();
            String html = "<body><input type='checkbox' hidden><input type='checkbox'>Accept terms"
                    + "<button id='first' data-testid='remove'>Delete</button>"
                    + "<button id='second' data-testid='remove'>Delete</button></body>";
            driver.get("data:text/html;base64," + java.util.Base64.getEncoder().encodeToString(
                    html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var candidates = DomCandidateExtractor.extract(HtmlSlimmer.slim(driver.getPageSource(), 80000));
            var checkbox = candidates.stream().filter(c -> c.value().equals("(//input)[2]")).findFirst().orElseThrow();
            driver.findElement(By.xpath(checkbox.value())).click();
            Assert.assertTrue(driver.findElements(By.tagName("input")).get(1).isSelected());
            Assert.assertFalse(driver.findElements(By.tagName("input")).get(0).isSelected());

            JavascriptExecutor js = (JavascriptExecutor) driver;
            var point = (List<?>) js.executeScript("const r=document.getElementById('second').getBoundingClientRect(); return [r.x+r.width/2,r.y+r.height/2];");
            SeleniumGroundingBrowser browser = new SeleniumGroundingBrowser(driver);
            var metrics = browser.metrics();
            VisualCandidate visual = new VisualCandidate("Delete", new BoundingBox(
                    ((Number) point.get(0)).intValue() - 8, ((Number) point.get(1)).intValue() - 8, 16, 16), .9);
            var hit = ElementGrounder.groundToHit(candidates, visual, browser,
                    metrics.innerWidth(), metrics.innerHeight(), metrics.innerWidth(), metrics.innerHeight()).orElseThrow();
            Assert.assertEquals(hit.table().stream().filter(c -> c.id().equals(hit.candidateId()))
                    .findFirst().orElseThrow().value(), "second");
            Assert.assertFalse(browser.matchesObservedNode("data-testid", "remove"), "duplicate hook cannot identify observed node");
            String before = browser.observationVersion();
            js.executeScript("document.getElementById('second').textContent='Moved'");
            Assert.assertNotEquals(browser.observationVersion(), before);
        } finally {
            factory.quit();
            if (old == null) System.clearProperty("EXECUTION_TYPE"); else System.setProperty("EXECUTION_TYPE", old);
        }
    }
}
