package delivery.ir;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TcDraftStoreTest {
    @Test
    public void roundTripDraft() throws Exception {
        Path work = Files.createTempDirectory("ir-store");
        TcDraftStore store = new TcDraftStore(work);
        ProvenStep step = new ProvenStep(
                "TC1", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        TcDraft draft = new TcDraft(
                "TC1", "Checkout", "1. Click checkout", "ok",
                TcDraftStatus.PARTIAL, List.of(step), List.of(), true,
                2, "Click Finish", "No DOM candidate", "evidence/TC1", 1,
                "https://example.com/checkout-step-two.html");
        store.write(draft);
        TcDraft loaded = store.read("TC1");
        Assert.assertEquals(loaded.tcId(), "TC1");
        Assert.assertEquals(loaded.status(), TcDraftStatus.PARTIAL);
        Assert.assertEquals(loaded.provenSteps().size(), 1);
        Assert.assertEquals(loaded.provenSteps().get(0).locatorValue(), "checkout");
        Assert.assertEquals(loaded.retryCountOnBlocker(), 1);
        Assert.assertEquals(store.readAll().size(), 1);
    }
}
