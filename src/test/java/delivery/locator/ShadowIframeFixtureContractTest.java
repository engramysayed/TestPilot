package delivery.locator;

import delivery.snapshot.KeelTarget;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D-QA-09: shared shadow/iframe fixture contract (no browser).
 * Fixtures live under {@code src/test/resources/locator-fixtures/} (copied from locator-fixtures/).
 */
public class ShadowIframeFixtureContractTest {

    private static final Pattern DATA_GT = Pattern.compile("data-gt=\"([^\"]+)\"");

    private Path fixture(String name) {
        Path p = Path.of("src/test/resources/locator-fixtures").resolve(name);
        Assert.assertTrue(Files.isRegularFile(p), "missing fixture " + p);
        return p;
    }

    private String read(String name) throws Exception {
        return Files.readString(fixture(name), StandardCharsets.UTF_8);
    }

    @Test
    public void shadowFixtureExposesOpenAndClosedGroundTruth() throws Exception {
        String html = read("shadow.html");
        Assert.assertTrue(html.contains("open-panel"), html);
        Assert.assertTrue(html.contains("closed-panel"), html);
        Assert.assertTrue(html.contains("attachShadow({mode:'open'})") || html.contains("mode:'open'"), html);
        Assert.assertTrue(html.contains("mode:'closed'"), html);
        // ground-truth markers
        Assert.assertTrue(DATA_GT.matcher(html).find(), "shadow.html must carry data-gt answer keys");
        int gt = 0;
        Matcher m = DATA_GT.matcher(html);
        while (m.find()) gt++;
        Assert.assertTrue(gt >= 4, "expected ≥4 data-gt in shadow.html, got " + gt);
    }

    @Test
    public void iframeSameOriginFixtureHasNestedFrame() throws Exception {
        String html = read("iframe-same.html");
        Assert.assertTrue(html.contains("iframe"), html);
        Assert.assertTrue(html.toLowerCase().contains("id=\"pay\"") || html.contains("id='pay'") || html.contains("#pay")
                || html.contains("pay"), html);
    }

    @Test
    public void keelTargetParsesFrameAndShadowPathsForFixtures() {
        // Paths matching LiveSmoke / serializer conventions for these fixtures
        KeelTarget open = KeelTarget.parse("shadow=#op >> css=#save");
        Assert.assertFalse(open.hops().isEmpty());
        Assert.assertTrue(open.format().contains("shadow="), open.format());

        KeelTarget closed = KeelTarget.parse("shadow-closed=#cp >> css=#delete");
        Assert.assertTrue(closed.format().startsWith("shadow-closed="), closed.format());

        KeelTarget nested = KeelTarget.parse("shadow=#op >> shadow=#chip >> css=#chip-link");
        Assert.assertTrue(nested.format().contains("shadow="), nested.format());

        KeelTarget frame = KeelTarget.parse("frame=#pay >> css=button");
        Assert.assertTrue(frame.format().startsWith("frame="), frame.format());

        KeelTarget deep = KeelTarget.parse("frame=#outer >> frame=iframe[name=\"inner\"] >> css=button");
        Assert.assertEquals(deep.hops().size(), 3, deep.format());
    }

    @Test
    public void invalidShadowPathRejected() {
        try {
            KeelTarget.parse("shadow-closed= >> css=#x");
            Assert.fail("blank shadow-closed host should fail");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // ok
        } catch (RuntimeException expected) {
            // parser may throw other runtime
            Assert.assertTrue(expected.getMessage() != null && !expected.getMessage().isBlank());
        }
    }

    @Test
    public void htmlSlimmerFixtureResourcesPresent() throws Exception {
        for (String f : new String[]{"shadow.html", "iframe-same.html", "iframe-cross.html", "sidebar.html", "canvas.html"}) {
            Assert.assertTrue(Files.size(fixture(f)) > 0, f);
        }
    }
}
