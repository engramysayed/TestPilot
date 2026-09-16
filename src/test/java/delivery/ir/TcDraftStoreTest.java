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

    @Test
    public void deleteAllRemovesDrafts() throws Exception {
        Path work = Files.createTempDirectory("ir-store-clear");
        TcDraftStore store = new TcDraftStore(work);
        store.write(new TcDraft(
                "TC1", "Checkout", "1. Click checkout", "ok",
                TcDraftStatus.TODO, List.of(), List.of(), false,
                -1, "", "", "", 0, ""));
        Assert.assertEquals(store.deleteAll(), 1);
        Assert.assertEquals(store.readAll().size(), 0);
    }

    @Test
    public void slashAndUnderscoreIdsDoNotOverwriteOneAnother() throws Exception {
        Path work = Files.createTempDirectory("ir-collide");
        TcDraftStore store = new TcDraftStore(work);
        store.write(new TcDraft(
                "TC_1", "Underscore", "1. Click", "ok",
                TcDraftStatus.PASSED, List.of(), List.of(), false,
                -1, "", "", "", 0, ""));
        store.write(new TcDraft(
                "TC/1", "Slash", "1. Click", "ok",
                TcDraftStatus.TODO, List.of(), List.of(), false,
                -1, "", "blocked", "", 0, ""));
        TcDraft underscore = store.read("TC_1");
        TcDraft slash = store.read("TC/1");
        Assert.assertEquals(underscore.title(), "Underscore");
        Assert.assertEquals(slash.title(), "Slash");
        Assert.assertEquals(store.readAll().size(), 2);
        Assert.assertTrue(Files.exists(work.resolve("ir").resolve(TcIdentity.storageKey("TC_1") + ".json")));
        Assert.assertTrue(Files.exists(work.resolve("ir").resolve(TcIdentity.storageKey("TC/1") + ".json")));
        Assert.assertNotEquals(TcIdentity.storageKey("TC_1"), TcIdentity.storageKey("TC/1"));
    }

    @Test
    public void legacyFileForADifferentDisplayIdIsNotSilentlyChosen() throws Exception {
        Path work = Files.createTempDirectory("ir-legacy");
        Path ir = work.resolve("ir");
        Files.createDirectories(ir);
        Files.writeString(ir.resolve(TcDraftStore.safeFileName("TC/1") + ".json"), """
                {"tcId":"TC/1","title":"slash","stepsText":"1. B","expectedResult":"ok","status":"TODO","provenSteps":[],"loginSteps":[]}
                """);
        TcDraftStore store = new TcDraftStore(work);
        try {
            store.read("TC_1");
            Assert.fail("expected ID_STORAGE_COLLISION or missing draft");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(
                    e.getMessage().startsWith("ID_STORAGE_COLLISION")
                            || e.getMessage().startsWith("IR draft not found"),
                    e.getMessage());
        }
        Assert.assertEquals(store.read("TC/1").title(), "slash");
    }
}
