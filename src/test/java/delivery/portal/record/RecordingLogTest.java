package delivery.portal.record;

import delivery.excel.GenerateQualityGate;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RecordingLogTest {

    @Test
    public void clickThenTypeKeepsBothAndUpdatesTheSameField() {
        RecordingLog log = new RecordingLog();
        String url = "https://shop.example/";
        log.accept("g1", List.of(new RecordingLog.Event("click", "Sign in", "", false)), url);
        log.accept("g1", List.of(
                new RecordingLog.Event("click", "Sign in", "", false),
                new RecordingLog.Event("type", "Email", "a", false)), url);
        List<String> steps = log.accept("g1", List.of(
                new RecordingLog.Event("click", "Sign in", "", false),
                new RecordingLog.Event("type", "Email", "ada@shop.example", false)), url);

        Assert.assertEquals(steps, List.of(
                "Open https://shop.example/",
                "Click \"Sign in\"",
                "Type \"ada@shop.example\" into \"Email\""));
    }

    @Test
    public void newPageKeepsEarlierSteps() {
        RecordingLog log = new RecordingLog();
        log.accept("g1", List.of(new RecordingLog.Event("click", "Sign in", "", false)), "https://shop.example/");
        List<String> steps = log.accept("g2",
                List.of(new RecordingLog.Event("click", "Pay", "", false)),
                "https://shop.example/pay");
        Assert.assertEquals(steps, List.of(
                "Open https://shop.example/",
                "Click \"Sign in\"",
                "Open https://shop.example/pay",
                "Click \"Pay\""));
    }

    @Test
    public void passwordValueIsNeverWritten() {
        RecordingLog log = new RecordingLog();
        List<String> steps = log.accept("g1", List.of(
                new RecordingLog.Event("type", "Password", "s3cret", true),
                new RecordingLog.Event("type", "Passcode", "s3cret", false)),
                "https://user:s3cret@shop.example/login");
        String text = RecordingLog.numbered(steps);
        Assert.assertFalse(text.contains("s3cret"));
        Assert.assertTrue(text.contains("Enter password into the \"Password\" field"));
        Assert.assertTrue(text.contains("Enter password into the \"Passcode\" field"));
        Assert.assertTrue(text.contains("Open https://shop.example/login"));
        Assert.assertTrue(GenerateQualityGate.validate(List.of(new ManualTestCase(
                "TC_1", "Sign in", "", text, "The account page is shown.", "P2", "recorded"
        )), "https://shop.example").isEmpty(), text);
    }

    @Test
    public void samePageNavigationIsKeptInOrder() {
        RecordingLog log = new RecordingLog();
        List<String> steps = log.accept("g1", List.of(
                new RecordingLog.Event("click", "Cart", "", false),
                new RecordingLog.Event("open", "https://shop.example/cart", "", false)),
                "https://shop.example/");
        Assert.assertEquals(steps.get(1), "Click \"Cart\"");
        Assert.assertEquals(steps.get(2), "Open https://shop.example/cart");
    }
}
