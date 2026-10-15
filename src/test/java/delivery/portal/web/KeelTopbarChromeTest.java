package delivery.portal.web;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pack 12 — topbar theme+EN one line; no initials avatar. */
public class KeelTopbarChromeTest {
    private static String read(String rel) throws IOException {
        Path p = Path.of(rel);
        if (!Files.isRegularFile(p)) {
            p = Path.of("src/main/resources").resolve(rel.replace("src/main/resources/", ""));
        }
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    public void topbarActionsHasThemeAndEnWithoutAvatar() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/templates/fragments.html"), StandardCharsets.UTF_8);
        int actions = html.indexOf("class=\"topbar-actions\"");
        Assert.assertTrue(actions > 0);
        String chunk = html.substring(actions, Math.min(html.length(), actions + 1200));
        Assert.assertTrue(chunk.contains("theme-toggle--icon"));
        Assert.assertTrue(chunk.contains("topbar-en-btn"));
        Assert.assertFalse(chunk.contains("avatar--top"));
        Assert.assertFalse(chunk.contains("userInitials"));
        Assert.assertTrue(chunk.contains("theme-ico--sun"));
        Assert.assertTrue(chunk.contains("theme-ico--moon"));
    }

    @Test
    public void calmForcesSingleRowActions() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/keel-theme-calm.css"), StandardCharsets.UTF_8);
        Assert.assertTrue(css.contains(".topbar-actions"));
        Assert.assertTrue(css.contains("flex-wrap: nowrap"));
        Assert.assertTrue(css.contains("height: 2rem"));
    }

    @Test
    public void themeJsTogglesSvgMoonSun() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/static/js/keel-theme.js"), StandardCharsets.UTF_8);
        Assert.assertTrue(js.contains("theme-ico--sun"));
        Assert.assertTrue(js.contains("theme-ico--moon"));
    }
}
