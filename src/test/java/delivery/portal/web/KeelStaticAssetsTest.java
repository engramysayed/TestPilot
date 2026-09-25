package delivery.portal.web;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fails CI if reskin static assets are missing from the classpath tree (untracked / not packaged).
 */
public class KeelStaticAssetsTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    @Test
    public void keelReskinCssPresent() throws Exception {
        Path css = STATIC.resolve("css/keel-reskin.css");
        Assert.assertTrue(Files.isRegularFile(css), "missing " + css);
        String body = Files.readString(css);
        Assert.assertTrue(body.contains(".keel-page") || body.contains(".topbar"),
                "keel-reskin.css should define reskin selectors");
    }

    @Test
    public void portalNavJsPresent() throws Exception {
        Path js = STATIC.resolve("js/portal-nav.js");
        Assert.assertTrue(Files.isRegularFile(js), "missing " + js);
        String body = Files.readString(js);
        Assert.assertTrue(body.contains("globalSearch") || body.contains("/api/portal/search"),
                "portal-nav.js should wire global search");
    }
}
