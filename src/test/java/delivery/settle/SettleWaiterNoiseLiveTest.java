package delivery.settle;

import org.openqa.selenium.By;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * r3: every SettleWaiterLiveTest case again, with BackgroundNoise running (other-origin timer fetch/XHR/img,
 * fake-extension same-origin fetches, chrome-extension:// requests). Default scope=page must be unaffected.
 */
public class SettleWaiterNoiseLiveTest extends SettleWaiterLiveTest {
    @Override protected void noise() {
        BackgroundNoise.inject(driver);
    }

    @AfterMethod(alwaysRun = true) public void clearScope() {
        System.clearProperty(NetworkScope.SCOPE);
        System.clearProperty(NetworkScope.ALLOW);
        System.clearProperty(NetworkScope.IGNORE);
    }

    @Test public void noiseIsSeenButIgnored() {
        var r = click("nothing", SettlePolicy.Action.CLICK);
        assertTrue(r.settled(), r.toString());
        var c = BackgroundNoise.counters(driver);
        assertTrue(((Number) c.get("ignored")).intValue() > 0, "noise reached the page tracker: " + c);
        assertEquals(((Number) c.get("counted")).intValue(), 0, "nothing the page started: " + c);
    }

    @Test public void legacyScopeAllIsBrokenByTheNoise() {
        System.setProperty(NetworkScope.SCOPE, "all");
        var typed = click("push", SettlePolicy.Action.TYPE);
        assertFalse(typed.settled());
        assertEquals(typed.unmet(), java.util.List.of("url-same"), "r3: url-same is reported alone even with noise");
        assertTrue(typed.elapsedMs() < 1000, "url-same still reported immediately (r3 fix): " + typed);
        var quiet = click("nothing", SettlePolicy.Action.CLICK);
        assertFalse(quiet.settled(), "scope=all reproduces the company-machine symptom: " + quiet);
        assertEquals(quiet.unmet(), java.util.List.of("network"));
    }

    @Test public void allowListedOtherOriginCountsAgain() {
        System.setProperty(NetworkScope.ALLOW, BackgroundNoise.otherOrigin());
        var r = click("nothing", SettlePolicy.Action.CLICK);
        assertFalse(r.settled(), "allowed noisy origin keeps network busy: " + r);
        assertEquals(r.unmet(), java.util.List.of("network"));
        System.setProperty(NetworkScope.IGNORE, "/beacon,/xhr,re:pixel\\\\.gif");
        var ok = click("nothing", SettlePolicy.Action.CLICK);
        assertTrue(ok.settled(), "ignore list wins over the allow list: " + ok);
    }

    @Test public void pageFetchStillCountsUnderNoise() {
        var r = click("fetch", SettlePolicy.Action.CLICK);
        assertTrue(r.settled(), r.toString());
        assertTrue(r.elapsedMs() >= 700, "waited for the page's own /slow: " + r);
        assertEquals(driver.findElement(By.id("out")).getText(), "slow-done");
    }
}
