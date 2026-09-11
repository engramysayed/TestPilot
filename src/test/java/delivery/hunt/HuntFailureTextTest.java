package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HuntFailureTextTest {

    private static final String SELENIUM_DUMP = """
            no such element: Unable to locate element: {"method":"css selector","selector":"a[aria-label='Close']"}
              (Session info: chrome=153.0.8010.37)
            For documentation on this error, please visit: https://www.selenium.dev/documentation
            Build info: version: '4.49.0', revision: 'bedb24f'
            System info: os.name: 'Windows 11', os.arch: 'amd64'
            Driver info: org.openqa.selenium.chrome.ChromeDriver
            Capabilities {acceptInsecureCerts: true, browserName: chrome}
            Session ID: c8036094b82fb8480b3016818dbc7ef6""";

    @Test
    public void shortenDropsSeleniumStackNoise() {
        String out = HuntFailureText.shorten(SELENIUM_DUMP);
        Assert.assertTrue(out.startsWith("no such element"), out);
        Assert.assertFalse(out.contains("Build info"), out);
        Assert.assertFalse(out.contains("Capabilities"), out);
        Assert.assertFalse(out.contains("chromedriverVersion"), out);
        Assert.assertTrue(out.length() <= HuntFailureText.MAX_CHARS + 1, "length=" + out.length());
    }

    @Test
    public void hunterMissDetectsLocatorAndTextMisses() {
        Assert.assertTrue(HuntFailureText.isHunterMiss(SELENIUM_DUMP));
        Assert.assertTrue(HuntFailureText.isHunterMiss("text not found on page"));
        Assert.assertTrue(HuntFailureText.isHunterMiss("stale element reference"));
        Assert.assertFalse(HuntFailureText.isHunterMiss("HTTP 400 from otp/verify"));
        Assert.assertFalse(HuntFailureText.isHunterMiss(""));
        Assert.assertFalse(HuntFailureText.isHunterMiss(null));
    }

    @Test
    public void journalReproKeepsSeleniumDumpOut() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("hunt-journal-clean");
        HuntStepsJournal journal = new HuntStepsJournal(root);
        journal.appendCycleHeader(1, "continue", "probe close button");
        journal.appendActions(1, java.util.List.of(java.util.Map.of(
                "type", "click", "status", "fail",
                "locator", "a[aria-label='Close']", "reason", SELENIUM_DUMP)));

        String repro = journal.reproSlice();
        Assert.assertTrue(repro.contains("no such element"), repro);
        Assert.assertFalse(repro.contains("Build info"), repro);
        Assert.assertFalse(repro.contains("Capabilities"), repro);
    }
}
