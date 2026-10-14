package delivery.portal.service.credits;

import delivery.portal.roles.ApprovalStatus;

import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;
import delivery.portal.PortalApplication;

/**
 * QA lock gates (2026-10-14): costs 10/5/25/15, enabled default false, debit:job:{id} idempotency,
 * INSUFFICIENT_CREDITS fields. Complements CreditsApiTest / CreditCostTableTest.
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "delivery.mail-enabled=false",
        "delivery.credits.enabled=true",
        "delivery.credits.cost.generate=10",
        "delivery.credits.cost.execute=5",
        "delivery.credits.cost.automate=25",
        "delivery.credits.cost.hunt=15",
        "delivery.store-root=./target/test-delivery-store-credits-gate",
        "delivery.work-dir=./target/test-delivery-work-credits-gate"
})
public class CreditsGateLockTest extends AbstractTestNGSpringContextTests {

    @Autowired private CreditService credits;
    @Autowired private CreditCostTable costs;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private DeliveryCreditsProperties props;

    private PortalUser approved(String prefix) {
        PortalUser u = new PortalUser();
        u.setEmail(prefix + "-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("GatePass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        return users.save(u);
    }

    private JobRecord job(String jobId, Long ownerId, JobRecord.JobKind kind) throws Exception {
        Path excel = Files.createTempFile("credits-gate-", ".xlsx");
        return new JobRecord(jobId, "proj_gate", ownerId, "NEW", excel, "https://example.com", "", "", false, kind);
    }

    @Test
    public void costMatrixMatchesQaLock() {
        Assert.assertEquals(costs.cost(CreditsBillingKind.GENERATE), 10L);
        Assert.assertEquals(costs.cost(CreditsBillingKind.EXECUTE), 5L);
        Assert.assertEquals(costs.cost(CreditsBillingKind.AUTOMATE), 25L);
        Assert.assertEquals(costs.cost(CreditsBillingKind.HUNT), 15L);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(JobRecord.JobKind.GENERATE_BATCH), CreditsBillingKind.GENERATE);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(JobRecord.JobKind.GENERATE_COMPARE), CreditsBillingKind.GENERATE);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(JobRecord.JobKind.CONVERT), CreditsBillingKind.AUTOMATE);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(JobRecord.JobKind.HUNT), CreditsBillingKind.HUNT);
    }

    @Test
    public void debitEachBillableKindOnce() throws Exception {
        PortalUser u = approved("matrix");
        credits.setCredits(u.getId(), "GRANT_DELTA", 1000, "pack", "g-" + UUID.randomUUID(), null);
        long bal = 1000L;
        JobRecord.JobKind[] kinds = {
                JobRecord.JobKind.GENERATE_BATCH,
                JobRecord.JobKind.GENERATE_COMPARE,
                JobRecord.JobKind.EXECUTE,
                JobRecord.JobKind.CONVERT,
                JobRecord.JobKind.HUNT
        };
        long[] expectCost = {10, 10, 5, 25, 15};
        for (int i = 0; i < kinds.length; i++) {
            String id = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            credits.tryDebitForJob(job(id, u.getId(), kinds[i]));
            bal -= expectCost[i];
            Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), bal, "after " + kinds[i]);
            // idempotent
            credits.tryDebitForJob(job(id, u.getId(), kinds[i]));
            Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), bal, "idempotent " + kinds[i]);
        }
        Assert.assertEquals(bal, 1000L - 10 - 10 - 5 - 25 - 15);
    }

    @Test
    public void insufficientCreditsExceptionCarriesLockFields() throws Exception {
        PortalUser u = approved("poor");
        // balance 0
        try {
            credits.tryDebitForJob(job("job_poor_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    u.getId(), JobRecord.JobKind.HUNT));
            Assert.fail("expected InsufficientCreditsException");
        } catch (InsufficientCreditsException e) {
            Assert.assertEquals(InsufficientCreditsException.CODE, "INSUFFICIENT_CREDITS");
            Assert.assertEquals(e.required(), 15L);
            Assert.assertEquals(e.balance(), 0L);
            Assert.assertEquals(e.jobKind(), "HUNT");
        }
    }

    @Test
    public void disabledFlagSkipsDebitEvenWithZeroBalance() throws Exception {
        boolean previous = props.isEnabled();
        try {
            props.setEnabled(false);
            Assert.assertFalse(costs.enabled());
            PortalUser u = approved("soft");
            // no grant — balance 0
            String id = "job_soft_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            credits.tryDebitForJob(job(id, u.getId(), JobRecord.JobKind.CONVERT)); // must no-op
            Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), 0L);
            Map<String, Object> usage = credits.usageForUser(u.getId(), null, null, null);
            Assert.assertTrue(((List<?>) usage.get("entries")).isEmpty());
        } finally {
            props.setEnabled(previous);
        }
    }
}
