package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.codegen.ProvenStep;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

public class ProviderUsageTest {

    @Test
    public void recordsActualHealAndPrecisionFallbackSeparatelyFromAllowlist() {
        JobRecord job = new JobRecord(
                "job_prov", "prj_1", 1L, "NEW", Path.of("in.xlsx"), "https://example", "", "");
        job.setProviderAllowlistSnapshot("cursor,ollama");
        job.setAuthoringEngine(AuthoringEngine.PRECISION);
        ProvenStep step = new ProvenStep(
                "TC1", "Home", "elementAction", "click",
                "id", "go", "", "text", "Welcome", true, "ok");
        TcDraft draft = new TcDraft(
                "TC1", "Home", "1. Click", "Welcome",
                TcDraftStatus.PASSED, List.of(step), List.of(), false,
                -1, "", "", "", 0, "https://example/home")
                .withHeal("ollama", "")
                .withPrecisionJob("precision", 2, true, "timeout");

        ProviderUsage.Snapshot snap = ProviderUsage.from(job, List.of(draft));
        Assert.assertEquals(snap.allowed(), "cursor,ollama");
        Assert.assertTrue(snap.used().contains("precision"), snap.used());
        Assert.assertTrue(snap.used().contains("keel"), snap.used());
        Assert.assertTrue(snap.used().contains("ollama"), snap.used());
        Assert.assertTrue(snap.fallbackUsed());
        Assert.assertEquals(snap.fallbackFrom(), "precision");
        Assert.assertEquals(snap.fallbackTo(), "keel");
        Assert.assertEquals(snap.fallbackReason(), "timeout");

        ProviderUsage.apply(job, List.of(draft));
        Assert.assertEquals(job.getProvidersUsed(), snap.used());
        Assert.assertTrue(job.isFallbackUsed());
        Assert.assertEquals(job.getFallbackReason(), "timeout");
    }

    @Test
    public void infersFallbackFromRecordedJobMessageWhenDraftsAreGone() {
        JobRecord job = new JobRecord(
                "job_hist", "prj_1", 1L, "NEW", Path.of("in.xlsx"), "https://example", "", "");
        job.setProviderAllowlistSnapshot("cursor");
        job.setAuthoringEngine(AuthoringEngine.PRECISION);
        job.setMessage("PRECISION_FALLBACK: Precision engine fell back to Keel (budget)");

        ProviderUsage.Snapshot snap = ProviderUsage.from(job, List.of());
        Assert.assertTrue(snap.fallbackUsed());
        Assert.assertEquals(snap.fallbackFrom(), "precision");
        Assert.assertEquals(snap.fallbackTo(), "keel");
        Assert.assertTrue(snap.used().contains("keel"), snap.used());
    }
}
