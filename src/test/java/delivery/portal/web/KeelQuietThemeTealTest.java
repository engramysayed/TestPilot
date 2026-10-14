package delivery.portal.web;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pack 10 — cheap static asserts for quiet teal calm theme + auth light default.
 * No Spring context; reads tip CSS/fragments from module working directory.
 */
public class KeelQuietThemeTealTest {

    private static String read(String rel) throws Exception {
        Path p = Path.of(rel);
        if (!Files.exists(p)) {
            p = Path.of("keel-portal").resolve(rel);
        }
        Assert.assertTrue(Files.exists(p), "missing " + rel);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    public void calmCss_retiresPeriwinkle_usesQuietTeal() throws Exception {
        String calm = read("src/main/resources/static/css/keel-theme-calm.css");
        Assert.assertFalse(calm.contains("#3d63dd"), "light periwinkle must be gone");
        Assert.assertFalse(calm.contains("#8aa4f5"), "dark periwinkle must be gone");
        Assert.assertTrue(calm.contains("--calm-accent: #087f78"), "light accent teal");
        Assert.assertTrue(calm.contains("--calm-accent: #7ecfc5"), "dark quiet teal");
        Assert.assertTrue(calm.contains("--teal: var(--calm-accent)"), "teal token chain");
        Assert.assertTrue(calm.contains("auth-shell"), "auth-shell token lock");
        Assert.assertTrue(calm.contains("create-panel"), "dark island override for create-panel");
    }

    @Test
    public void authPageHead_forcesLight_notStoredDark() throws Exception {
        String frag = read("src/main/resources/templates/fragments.html");
        int auth = frag.indexOf("authPageHead");
        Assert.assertTrue(auth >= 0);
        String authBlock = frag.substring(auth, Math.min(frag.length(), auth + 900));
        Assert.assertTrue(authBlock.contains("auth-shell"));
        Assert.assertTrue(authBlock.contains("setAttribute('data-theme', 'light')"));
        Assert.assertFalse(authBlock.contains("|| 'dark'"));
    }

    @Test
    public void reskin_panelSurfaces_useTokens_notHardWhite() throws Exception {
        String reskin = read("src/main/resources/static/css/keel-reskin.css");
        Assert.assertTrue(reskin.contains(".keel-page .panel") && reskin.contains("var(--panel)"));
        Assert.assertTrue(reskin.contains("create-panel") && reskin.contains("var(--panel)"));
        Assert.assertFalse(reskin.contains("#80d1bf"), "neon mint foot retired");
    }
}
