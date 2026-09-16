package delivery.hunt;

import delivery.identity.TenantId;
import delivery.job.ConversionJobRequest;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;

public class HuntRuntimeFactoryTenantTest {

    @Test
    public void loginRequestCarriesPersistedTenantAndJobIdentity() {
        TenantId tenant = TenantId.mint();
        JobRecord job = new JobRecord(
                "hunt_abc123def456",
                "prj_same",
                11L,
                "HUNT",
                Path.of("request.json"),
                "https://same.example.com",
                "user",
                "pass",
                false,
                JobRecord.JobKind.HUNT
        );
        job.setTenantId(tenant.value());
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        ConversionJobRequest request = HuntRuntimeFactory.loginRequest(job, props);
        Assert.assertEquals(request.tenantId(), tenant);
        Assert.assertEquals(request.jobId(), "hunt_abc123def456");
        Assert.assertEquals(request.storeRoot(), Path.of(props.getStoreRoot()));
    }
}
