package delivery.job;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExecuteRunArtifactsTest {

    @Test
    public void syncTcCopiesIrDraftWhenPresent() throws Exception {
        Path work = Files.createTempDirectory("exec-work");
        Path dest = Files.createTempDirectory("exec-dest");
        TcDraftStore drafts = new TcDraftStore(work);
        TcDraft draft = new TcDraft(
                "TC_001", "Login", "1. Open app", "Logged in",
                TcDraftStatus.PASSED, List.of(), List.of(), false,
                -1, "", "", "", 0, "");
        drafts.write(draft);

        ExecuteRunArtifacts.syncTc(work, dest, "TC_001");

        Path copied = dest.resolve("ir").resolve(TcDraftStore.safeFileName("TC_001") + ".json");
        Assert.assertTrue(Files.isRegularFile(copied));
        Assert.assertEquals(TcDraftStore.fromJson(
                new org.json.JSONObject(Files.readString(copied))).tcId(), "TC_001");
    }

    @Test
    public void syncTcNoOpWhenIrMissing() throws Exception {
        Path work = Files.createTempDirectory("exec-work");
        Path dest = Files.createTempDirectory("exec-dest");

        ExecuteRunArtifacts.syncTc(work, dest, "TC_MISSING");

        Assert.assertFalse(Files.exists(dest.resolve("ir")));
    }
}
