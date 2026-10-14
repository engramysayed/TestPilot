package delivery.portal.worker;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.service.PortalStore;
import delivery.excel.ManualTestCase;
import delivery.excel.ManualTcExcelWriter;
import delivery.portal.jobs.DurableJobClaim;
import delivery.identity.TenantId;
import org.springframework.mock.env.MockEnvironment;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;

public class ConversionWorkerFenceTest {
    @Test public void leaseLossPreservesReconciledUncertainOutcome() throws Exception {
        exercise(false);
    }
    @Test public void explicitCancellationStillTerminatesAsCancelled() throws Exception {
        exercise(true);
    }
    private void exercise(boolean requested) throws Exception {
        Path root=Files.createTempDirectory("worker-fence");Path excel=root.resolve("suite.xlsx");
        ManualTcExcelWriter.write(excel,List.of(new ManualTestCase("TC_01","One","","Open page","Page","P1","")));
        var props=new DeliveryPortalProperties();props.setDryRun(true);props.setStoreRoot(root.resolve("store").toString());
        props.setWorkDir(root.resolve("work").toString());props.setTemplateRoot("customer-framework-template");
        var job=new JobRecord("job_fence","project",1L,"NEW",excel,"https://example.invalid","","");
        job.setTenantId(TenantId.mint().value());
        var lease=DurableJobClaim.tryClaim(job,"worker",Instant.now(),Duration.ofSeconds(45)).orElseThrow();
        PortalStore store=mock(PortalStore.class);
        when(store.getJob(job.getJobId())).thenReturn(Optional.of(job));
        when(store.beginWork(job.getJobId())).thenReturn(Optional.of(lease));
        when(store.precisionConfigForJob(job)).thenReturn(new delivery.authoring.PrecisionJobConfig(false,50));
        when(store.ownsAttempt(job,lease)).thenReturn(false);
        when(store.shouldAbortCompletion(job)).thenReturn(requested);
        when(store.reconcileExpiredLeases()).thenAnswer(call->{
            job.setStatus(JobRecord.Status.FAILED);job.setError(DurableJobClaim.INTERRUPTED_UNCERTAIN);return 1;
        });
        new ConversionWorker(props,store,new MockEnvironment()).submit(job.getJobId());
        Assert.assertEquals(job.getStatus(),requested?JobRecord.Status.CANCELLED:JobRecord.Status.FAILED);
        if(!requested)Assert.assertEquals(job.getError(),DurableJobClaim.INTERRUPTED_UNCERTAIN);
    }
}
