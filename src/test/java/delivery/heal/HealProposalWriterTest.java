package delivery.heal;

import delivery.codegen.ProvenStep;
import delivery.evidence.Phase2EvidenceConfig;
import delivery.prompts.PromptCatalog;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HealProposalWriterTest {

    @AfterMethod
    public void clear() {
        System.clearProperty(Phase2EvidenceConfig.PROP_HEAL_PROPOSE_ONLY);
    }

    @Test
    public void emitPendingWritesStatusPendingNeverNeedsAccept() throws Exception {
        Path tmp = Files.createTempDirectory("heal-proposal-writer-");
        Path file = HealProposalWriter.emitPending(
                tmp, "proj", "job", "TC_1", 1, "Login_Page",
                new HealProposal.LocatorRef("css", "#old"),
                new HealProposal.LocatorRef("css", "#new"),
                "retry", "named control", "evidence/TC_1/failure/1");
        Assert.assertNotNull(file);
        Assert.assertTrue(Files.isRegularFile(file));
        JSONObject o = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
        Assert.assertEquals(o.getString("status"), "pending");
        Assert.assertEquals(o.getString("patchKind"), "locator");
        Assert.assertEquals(o.getString("pageClass"), "Login_Page");
        Assert.assertEquals(o.getJSONObject("locatorAfter").getString("value"), "#new");
        Assert.assertTrue(o.isNull("decidedAt"));
        Assert.assertTrue(o.has("promptsVersion"));
        Assert.assertEquals(o.getString("promptsVersion"), PromptCatalog.version());
        Assert.assertFalse(o.getString("promptsVersion").isBlank());
    }

    @Test
    public void skipsWhenPageClassMissing() throws Exception {
        Path tmp = Files.createTempDirectory("heal-proposal-skip-");
        Path file = HealProposalWriter.emitPending(
                tmp, "proj", "job", "TC_1", 0, "  ",
                new HealProposal.LocatorRef("css", "#a"),
                new HealProposal.LocatorRef("css", "#b"),
                "retry", "x", null);
        Assert.assertNull(file);
        Assert.assertFalse(Files.isDirectory(tmp.resolve("heal-proposals")));
    }

    @Test
    public void emitFromHealResultUsesExistingPageName() throws Exception {
        Path tmp = Files.createTempDirectory("heal-proposal-from-result-");
        ProvenStep step = new ProvenStep(
                "TC_1", "Inventory_Page", "action", "click", "css", "#add",
                "", "", "", true, "", "", false);
        HealResult healed = HealResult.success(List.of(step), "retry");
        Path file = HealProposalWriter.emitFromHealResult(
                tmp, "proj", "job", "TC_1", 3,
                List.of(new FailedLocator("css", "#gone", "stale")),
                healed, null);
        Assert.assertNotNull(file);
        JSONObject o = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
        Assert.assertEquals(o.getString("pageClass"), "Inventory_Page");
        Assert.assertEquals(o.getJSONObject("locatorBefore").getString("value"), "#gone");
        Assert.assertEquals(o.getJSONObject("locatorAfter").getString("value"), "#add");
        Assert.assertEquals(o.getInt("stepIndex"), 3);
        Assert.assertEquals(o.getString("promptsVersion"), PromptCatalog.version());
    }

    @Test
    public void stampsPromptsVersionFromCatalog() throws Exception {
        Path tmp = Files.createTempDirectory("heal-proposal-prompts-ver-");
        Path file = HealProposalWriter.emitPending(
                tmp, "proj", "job", "TC_1", 0, "Login_Page",
                new HealProposal.LocatorRef("css", "#a"),
                new HealProposal.LocatorRef("css", "#b"),
                "retry", "stamp check", null);
        Assert.assertNotNull(file);
        JSONObject o = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
        Assert.assertEquals(o.getString("promptsVersion"), PromptCatalog.version());
    }
}