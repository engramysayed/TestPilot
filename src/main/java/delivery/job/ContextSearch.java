package delivery.job;

import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds elements across default content, iframes, and open shadow roots.
 * Callers should return to default content when finished if they switched frames.
 */
public final class ContextSearch {
    private ContextSearch() {
    }

    public record Hit(WebElement element, String contextNote) {
    }

    public static Hit find(WebDriverFactory factory, By by) {
        WebDriver driver = factory.get();
        try {
            driver.switchTo().defaultContent();
        } catch (Exception ignored) {
        }
        Hit direct = tryFind(driver, by, "default");
        if (direct != null) {
            return direct;
        }
        // Open shadow roots under document
        Hit shadow = findInOpenShadows(driver, by);
        if (shadow != null) {
            return shadow;
        }
        List<WebElement> frames = driver.findElements(By.cssSelector("iframe, frame"));
        for (int i = 0; i < frames.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                Hit inFrame = tryFind(driver, by, "iframe[" + i + "]");
                if (inFrame != null) {
                    return inFrame;
                }
                Hit shadowInFrame = findInOpenShadows(driver, by);
                if (shadowInFrame != null) {
                    return new Hit(shadowInFrame.element(), "iframe[" + i + "]/" + shadowInFrame.contextNote());
                }
            } catch (Exception ignored) {
            }
        }
        try {
            driver.switchTo().defaultContent();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Hit tryFind(SearchContext ctx, By by, String note) {
        try {
            List<WebElement> list = ctx.findElements(by);
            Hit first = null;
            for (WebElement el : list) {
                if (el == null) {
                    continue;
                }
                if (first == null) {
                    first = new Hit(el, note);
                }
                try {
                    if (el.isDisplayed()) {
                        return new Hit(el, note);
                    }
                } catch (Exception ignored) {
                }
            }
            return first;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Hit findInOpenShadows(WebDriver driver, By by) {
        if (!(driver instanceof JavascriptExecutor js)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            List<WebElement> hosts = (List<WebElement>) js.executeScript(
                    """
                            const out = [];
                            function walk(root) {
                              if (!root) return;
                              const all = root.querySelectorAll ? root.querySelectorAll('*') : [];
                              for (const el of all) {
                                if (el.shadowRoot) {
                                  out.push(el);
                                  walk(el.shadowRoot);
                                }
                              }
                            }
                            walk(document);
                            return out;
                            """);
            if (hosts == null) {
                return null;
            }
            for (WebElement host : hosts) {
                try {
                    SearchContext shadow = host.getShadowRoot();
                    Hit hit = tryFind(shadow, by, "shadow");
                    if (hit != null) {
                        return hit;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** List iframe descriptors for candidate tables / prompts. */
    public static List<String> listIframeHints(WebDriverFactory factory) {
        List<String> out = new ArrayList<>();
        try {
            WebDriver driver = factory.get();
            driver.switchTo().defaultContent();
            List<WebElement> frames = driver.findElements(By.cssSelector("iframe, frame"));
            for (int i = 0; i < frames.size(); i++) {
                WebElement f = frames.get(i);
                String name = f.getAttribute("name");
                String id = f.getAttribute("id");
                String src = f.getAttribute("src");
                out.add("iframe[" + i + "] name=" + nullToEmpty(name)
                        + " id=" + nullToEmpty(id) + " src=" + nullToEmpty(src));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
