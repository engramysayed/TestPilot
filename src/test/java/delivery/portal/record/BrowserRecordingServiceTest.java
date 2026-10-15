package delivery.portal.record;

import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.GeneratedWorkbookService;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BrowserRecordingServiceTest {

    @Test
    public void pollShowsClicksAndStopClosesChrome() {
        List<FakeBrowser> opened = new ArrayList<>();
        BrowserRecordingService service = service(opened);
        BrowserRecordingService.View started = service.start(
                "user-1", "prj", "https://shop.example", "", true);
        Assert.assertEquals(opened.size(), 1);
        Assert.assertEquals(opened.get(0).url, "https://shop.example");
        Assert.assertEquals(started.steps().get(0), "Open https://shop.example");

        opened.get(0).payload = """
                {"gen":"g1","steps":[{"k":"click","label":"Sign in","value":"","secret":false}]}""";
        BrowserRecordingService.View polled = service.poll("user-1", "prj", started.sessionId());
        Assert.assertTrue(polled.steps().contains("Click \"Sign in\""));

        service.stop("user-1", "prj", started.sessionId());
        Assert.assertTrue(opened.get(0).closed);
    }

    @Test
    public void passwordFromThePageIsNotStored() {
        List<FakeBrowser> opened = new ArrayList<>();
        BrowserRecordingService service = service(opened);
        BrowserRecordingService.View started = service.start(
                "user-1", "prj", "https://shop.example", "https://shop.example/login", false);
        opened.get(0).payload = """
                {"gen":"g1","steps":[{"k":"type","label":"Password","value":"s3cret","secret":true}]}""";
        BrowserRecordingService.View polled = service.poll("user-1", "prj", started.sessionId());
        String text = String.join("\n", polled.steps());
        Assert.assertFalse(text.contains("s3cret"), text);
        Assert.assertTrue(text.contains("Enter password"));
        service.stop("user-1", "prj", started.sessionId());
    }

    @Test
    public void anotherWebsiteDoesNotOpenChrome() {
        List<FakeBrowser> opened = new ArrayList<>();
        BrowserRecordingService service = service(opened);
        try {
            service.start("user-1", "prj", "https://shop.example", "https://evil.test", false);
            Assert.fail("expected a refusal");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("project's website"));
        }
        Assert.assertTrue(opened.isEmpty());
    }

    @Test
    public void aSecondRecordingClosesTheFirstWindow() {
        List<FakeBrowser> opened = new ArrayList<>();
        BrowserRecordingService service = service(opened);
        service.start("user-1", "prj", "https://shop.example", "", true);
        service.start("user-1", "prj", "https://shop.example/cart", "", true);
        Assert.assertEquals(opened.size(), 2);
        Assert.assertTrue(opened.get(0).closed);
        Assert.assertFalse(opened.get(1).closed);
        opened.get(1).close();
    }

    @Test
    public void saveWritesTheRecordedCase() throws Exception {
        List<FakeBrowser> opened = new ArrayList<>();
        GeneratedWorkbookService workbooks = workbooks();
        String projectId = "proj-record-" + UUID.randomUUID();
        BrowserRecordingService service = new BrowserRecordingService(workbooks, (origin, url) -> track(opened, url));
        BrowserRecordingService.View started = service.start(
                "user-1", projectId, "https://shop.example", "", true);
        opened.get(0).payload = """
                {"gen":"g1","steps":[{"k":"click","label":"Pay","value":"","secret":false}]}""";
        service.poll("user-1", projectId, started.sessionId());
        var saved = service.save("user-1", projectId, started.sessionId(),
                "Pay with a saved card", "The order confirmation page shows an order number.", true,
                "https://shop.example");
        Assert.assertEquals(saved.get("tcId"), "TC_1");
        List<ManualTestCase> cases = workbooks.readCases(projectId);
        Assert.assertEquals(cases.size(), 1);
        Assert.assertTrue(cases.get(0).steps().contains("Click \"Pay\""));
        Assert.assertEquals(cases.get(0).preconditions(),
                "Sign in with a project login account before these steps.");
        Assert.assertTrue(opened.get(0).closed);
    }

    private static BrowserRecordingService service(List<FakeBrowser> opened) {
        return new BrowserRecordingService(null, (origin, url) -> track(opened, url));
    }

    private static FakeBrowser track(List<FakeBrowser> opened, String url) {
        FakeBrowser browser = new FakeBrowser();
        browser.url = url;
        browser.location = url;
        browser.payload = "{\"gen\":\"g1\",\"steps\":[]}";
        opened.add(browser);
        return browser;
    }

    private static GeneratedWorkbookService workbooks() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-record");
        return new GeneratedWorkbookService(props);
    }

    private static final class FakeBrowser implements RecordingBrowser {
        String url;
        String location = "";
        String payload = "";
        boolean closed;
        boolean alive = true;

        @Override
        public String location() {
            return location;
        }

        @Override
        public String readEvents() {
            return payload;
        }

        @Override
        public void ensureInstalled() {
        }

        @Override
        public boolean alive() {
            return alive && !closed;
        }

        @Override
        public void close() {
            closed = true;
            alive = false;
        }
    }
}
