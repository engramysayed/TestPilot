package delivery.job;

import delivery.authoring.StepIntentBinder;
import org.openqa.selenium.*;
import java.util.regex.Pattern;

/** Bounded reveal of a named sidebar control before spending model calls. Never clicks. */
public final class NamedNavigationDiscovery {
    private NamedNavigationDiscovery() {}
    public static boolean reveal(WebDriver driver, StepIntentBinder.IntentLine intent) {
        if (!(driver instanceof JavascriptExecutor js) || intent == null
                || intent.kind() != StepIntentBinder.IntentKind.CLICK) return false;
        if (!intent.text().toLowerCase(java.util.Locale.ROOT).matches(".*(sidebar|side menu|navigation|sub-menu|submenu).*")) return false;
        var quoted = Pattern.compile("[\"']([^\"']+)[\"']").matcher(intent.text());
        if (!quoted.find()) return false;
        String name = quoted.group(1);
        Object state = js.executeScript("return Array.from(document.querySelectorAll('*')).filter(e=>e.scrollHeight>e.clientHeight+2&&e.clientHeight>0).map(e=>[e,e.scrollLeft,e.scrollTop])");
        boolean found = false;
        try {
            for (int i = 0; i < 8; i++) {
                JobCancelSupport.checkCancelled(() -> Thread.currentThread().isInterrupted());
                if (Boolean.TRUE.equals(js.executeScript("""
                    const name=arguments[0].trim().toLowerCase();
                    const matches=Array.from(document.querySelectorAll('a,button,[role=menuitem],[role=link],[role=button],li,span,div'))
                      .filter(e=>e.textContent.trim().toLowerCase()===name && e.getClientRects().length && getComputedStyle(e).visibility!=='hidden');
                    const leaves=matches.filter(e=>!matches.some(other=>other!==e&&e.contains(other)));
                    if(leaves.length!==1) return false;
                    leaves[0].scrollIntoView({block:'center',inline:'nearest',behavior:'instant'});
                    return true;
                    """, name))) {
                    found = true;
                    utils.LogsManager.info("NAV_DISCOVERY: revealed named control before binding");
                    return true;
                }
                new delivery.vision.SeleniumGroundingBrowser(driver).scrollViewport();
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
            return false;
        } finally {
            if (!found && state instanceof java.util.List<?> positions) {
                js.executeScript("for(const p of arguments[0]){if(p[0].isConnected){p[0].scrollLeft=p[1];p[0].scrollTop=p[2]}}", positions);
            }
        }
    }
}
