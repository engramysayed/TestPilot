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

    @Test
    public void keelThemeCalmCssPresent() throws Exception {
        Path css = STATIC.resolve("css/keel-theme-calm.css");
        Assert.assertTrue(Files.isRegularFile(css), "missing " + css);
        String body = Files.readString(css);
        Assert.assertTrue(body.contains("--calm-accent") && body.contains("data-theme"),
                "keel-theme-calm.css should define calm tokens and dark theme");
    }

    @Test
    public void keelThemeJsPresent() throws Exception {
        Path js = STATIC.resolve("js/keel-theme.js");
        Assert.assertTrue(Files.isRegularFile(js), "missing " + js);
        String body = Files.readString(js);
        Assert.assertTrue(body.contains("keel-theme") && body.contains("data-theme"),
                "keel-theme.js should toggle data-theme");
        Assert.assertTrue(body.contains("apply('dark')") || body.contains("apply(\"dark\")"),
                "keel-theme.js should default to dark when no preference stored");
    }
}
