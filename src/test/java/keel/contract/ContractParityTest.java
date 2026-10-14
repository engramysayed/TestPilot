package keel.contract;

import delivery.portal.jobs.DurableJobClaim;
import delivery.job.WebhookSigner;
import delivery.portal.model.JobRecord;
import delivery.runner.PrivateRunnerArtifacts;
import keel.contract.runner.WireJobKind;
import keel.contract.runner.WireStage;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.testng.Assert.assertEquals;

/** B03: the contract module mirrors the portal's domain enums and keeps the v1 artifact signature byte-identical. */
public class ContractParityTest {
    @Test
    public void jobKindAndStageNamesMatch() {
        assertEquals(names(WireJobKind.values()), names(JobRecord.JobKind.values()));
        assertEquals(names(WireStage.values()), names(DurableJobClaim.Stage.values()));
    }

    @Test
    public void artifactSignatureUnchangedVersusLegacyWebhookSigner() {
        byte[] body = "artifact-bytes".getBytes(StandardCharsets.UTF_8);
        String legacy = WebhookSigner.signature("tp_run_abc", "job-1.att-1", PrivateRunnerArtifacts.digest(body));
        assertEquals(PrivateRunnerArtifacts.signature("tp_run_abc", "job-1", "att-1", body), legacy);
    }

    private static String names(Enum<?>[] values) {
        return Arrays.toString(Arrays.stream(values).map(Enum::name).toArray());
    }
}
