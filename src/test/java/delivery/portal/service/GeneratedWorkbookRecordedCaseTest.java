package delivery.portal.service;

import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.UUID;

public class GeneratedWorkbookRecordedCaseTest {

    @Test
    public void appendCreatesTheLibraryThenTheNextId() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-recorded-" + UUID.randomUUID();
        var first = service.appendRecordedCase(
                projectId,
                "Pay with a saved card",
                "1. Open https://shop.example\n2. Click \"Pay\"",
                "The order confirmation page shows an order number.",
                "",
                "https://shop.example");
        Assert.assertEquals(first.get("tcId"), "TC_1");

        service.saveFromCases(projectId, List.of(
                new ManualTestCase("TC_9", "Existing", "", "1. Open https://shop.example", "Shown.", "P2", "")
        ), "GENERATE", "unit");
        var second = service.appendRecordedCase(
                projectId,
                "Second path",
                "1. Open https://shop.example\n2. Click \"Cart\"",
                "The cart page is shown.",
                "Sign in with a project login account before these steps.",
                "https://shop.example");
        Assert.assertEquals(second.get("tcId"), "TC_10");
        Assert.assertEquals(service.readCases(projectId).size(), 2);
    }

    @Test
    public void recordedCaseStoresAuthorNotTcId() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-recorded-author-" + UUID.randomUUID();
        service.appendRecordedCase(
                projectId,
                "Pay with a saved card",
                "1. Open https://shop.example\n2. Click \"Pay\"",
                "The order confirmation page shows an order number.",
                "",
                "https://shop.example",
                "ramy@example.com");
        Assert.assertEquals(service.describe(projectId).orElseThrow().get("sourceRef"), "ramy@example.com");
        Assert.assertEquals(service.describe(projectId).orElseThrow().get("source"), "RECORD");
    }

    @Test
    public void blankStepsAreRefused() throws Exception {
        GeneratedWorkbookService service = service();
        try {
            service.appendRecordedCase("proj-blank-" + UUID.randomUUID(), "Name", "   ", "Done.", "", "https://shop.example");
            Assert.fail("expected a refusal");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("at least one step"));
        }
    }

    private static GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-record");
        return new GeneratedWorkbookService(props);
    }
}
