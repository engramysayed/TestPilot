package delivery.job;

import delivery.authoring.StepIntentBinder;
import delivery.vision.SeleniumGroundingBrowser;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.SkipException;
import org.testng.annotations.Test;
import java.nio.file.*;
import static org.testng.Assert.*;

public class SidebarDiscoveryLiveSmokeTest {
    @Test public void nestedSidebarScrollsAndMissingTargetsRestorePosition() throws Exception {
        String executable=System.getProperty("keel.performance.chromedriver", "");
        if(executable.isBlank()) throw new SkipException("Existing driver required");
        Path page=Files.createTempFile("sidebar-fixture-", ".html");
        ChromeDriver driver=null;
        try {
            Files.writeString(page,"<html><body style='height:100vh;overflow:hidden'><aside id='menu' style='height:180px;width:250px;overflow-y:auto'>"
                    + "<ul><li style='height:60px'>Dashboard</li>".repeat(1)
                    + "<li style='height:60px'>Other item</li>".repeat(12)
                    + "<li id='support' style='height:60px'>Support</li></ul></aside><main>Page</main></body></html>");
            driver=new ChromeDriver(new ChromeDriverService.Builder().usingDriverExecutable(Path.of(executable).toFile()).build(),
                    new ChromeOptions().addArguments("--headless=new","--window-size=1000,700"));
            driver.get(page.toUri().toString());
            var browser=new SeleniumGroundingBrowser(driver);
            Object saved=browser.saveScroll(); browser.scrollViewport();
            assertTrue(((Number)driver.executeScript("return document.getElementById('menu').scrollTop")).intValue()>0);
            browser.restoreScroll(saved);
            assertEquals(((Number)driver.executeScript("return document.getElementById('menu').scrollTop")).intValue(),0);
            assertTrue(NamedNavigationDiscovery.reveal(driver,new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,"Clicks \"Support\" in the sidebar.")));
            assertTrue((Boolean)driver.executeScript("const r=document.getElementById('support').getBoundingClientRect(),p=document.getElementById('menu').getBoundingClientRect();return r.top>=p.top&&r.bottom<=p.bottom"));
            String slim=parsingLayer.HtmlSlimmer.slim(PageSnapshot.html(driver),1200);
            var candidates=delivery.authoring.DomCandidateExtractor.extract(slim);
            var support=candidates.stream().filter(c->c.label().equals("Support")).findFirst()
                    .orElseThrow(() -> new AssertionError("SLIM="+slim+" CANDIDATES="+candidates));
            var bound=StepIntentBinder.bindSingle(new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,
                    "Clicks \"Support\" in the sidebar."),"TC",java.util.List.of(support),java.util.List.of(support.id()));
            assertTrue(bound.ok(),bound.rejectReason());
            var step=bound.steps().get(0);
            assertEquals(driver.findElement(executionLayer.SelectorParser.toBy(step.locatorStrategy()+":"+step.locatorValue())).getText(),"Support");
            int before=((Number)driver.executeScript("return document.getElementById('menu').scrollTop")).intValue();
            assertFalse(NamedNavigationDiscovery.reveal(driver,new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,"Click \"Missing\" in the sidebar")));
            assertEquals(((Number)driver.executeScript("return document.getElementById('menu').scrollTop")).intValue(),before);
        } finally { try {if(driver!=null)driver.quit();} finally {Files.deleteIfExists(page);} }
    }
}
