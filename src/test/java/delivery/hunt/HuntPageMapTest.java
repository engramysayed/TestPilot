package delivery.hunt;

import delivery.authoring.DomCandidate;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class HuntPageMapTest {
    @Test
    public void buildsControlsAndDetectsThin() {
        String html = """
                <html><body>
                  <h1>Login</h1>
                  <div role="alert">Bad password</div>
                  <input id="user" name="user" />
                  <button id="go">Sign in</button>
                </body></html>
                """;
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/login", "Login", html);
        Assert.assertEquals(map.url(), "https://ex/login");
        Assert.assertTrue(map.controlCount() >= 2);
        // Threshold: controlCount() < 8 ⇒ thin. This fixture has ~2 controls.
        Assert.assertTrue(map.isThin());
        Assert.assertTrue(map.toPromptMd().contains("Login"));
        Assert.assertTrue(map.toPromptMd().contains("Bad password")
                || map.alerts().stream().anyMatch(a -> a.contains("Bad")));
    }

    @Test
    public void emptyBodyIsThin() {
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/", "x", "<html><body></body></html>");
        Assert.assertTrue(map.isThin());
        Assert.assertEquals(map.controlCount(), 0);
    }

    @Test
    public void toPromptMd_includesDialogsSection() {
        String html = """
                <html><body>
                  <div role="dialog" aria-label="Confirm delete">Are you sure?</div>
                </body></html>
                """;
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/modal", "Modal", html);
        String md = map.toPromptMd();
        Assert.assertTrue(md.contains("## Dialogs"));
        Assert.assertTrue(md.contains("Confirm delete"));
    }

    @Test
    public void toPromptMd_capsAtPromptMaxWhenControlsOverflow() {
        List<DomCandidate> controls = new ArrayList<>();
        String longLabel = "x".repeat(500);
        for (int i = 0; i < 100; i++) {
            controls.add(new DomCandidate("id" + i, "css", "#ctrl" + i, "button", longLabel + i));
        }
        HuntPageMap map = new HuntPageMap(
                "https://ex/big",
                "Big page",
                List.of("Primary heading"),
                List.of("Session expired"),
                controls,
                List.of("Unsaved changes"));
        String md = map.toPromptMd();
        Assert.assertTrue(md.length() <= HuntPageMap.PROMPT_MAX_CHARS,
                "prompt length " + md.length() + " exceeds cap " + HuntPageMap.PROMPT_MAX_CHARS);
        Assert.assertTrue(md.contains("Primary heading"));
        Assert.assertTrue(md.contains("Session expired"));
        Assert.assertTrue(md.contains("Unsaved changes"));
        Assert.assertTrue(md.contains("#ctrl0"));
        Assert.assertFalse(md.contains("#ctrl99"), "lowest-priority controls should be dropped first");
    }
}
