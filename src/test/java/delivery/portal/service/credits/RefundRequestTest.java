package delivery.portal.service.credits;

import delivery.job.RetryCategory;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.credits.CreditLedgerEntry;
import delivery.portal.persistence.credits.CreditLedgerRepository;
import delivery.portal.persistence.credits.RefundRequest;
import delivery.portal.persistence.credits.RefundRequestRepository;
import delivery.portal.roles.ApprovalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.UUID;

@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "delivery.mail-enabled=false",
        "delivery.credits.enabled=true",
        "delivery.credits.refunds.auto-open=false",
        "spring.datasource.url=jdbc:h2:mem:refund-requests;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "delivery.store-root=./target/test-delivery-store-refunds",
        "delivery.work-dir=./target/test-delivery-work-refunds"
})
public class RefundRequestTest extends AbstractTestNGSpringContextTests {

    @Autowired private CreditService credits;
    @Autowired private RefundRequestService refunds;
    @Autowired private RefundRequestRepository requests;
    @Autowired private CreditLedgerRepository ledger;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;

    private PortalUser user() {
        PortalUser u = new PortalUser();
        u.setEmail("refund-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("RefundPass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        return users.save(u);
    }

    /** Simulates tryDebitForJob's ledger row (balance already reduced). */
    private String chargedJob(PortalUser u, long cost) {
        credits.setCredits(u.getId(), "SET_ABSOLUTE", 100 - cost, "balance after simulated debit", "s-" + UUID.randomUUID(), null);
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        CreditLedgerEntry e = new CreditLedgerEntry();
        e.setUserId(u.getId());
        e.setDelta(-cost);
        e.setBalanceAfter(100 - cost);
        e.setReason(CreditService.REASON_JOB_DEBIT);
        e.setJobId(jobId);
        e.setJobKind("EXECUTE");
        e.setIdempotencyKey("debit:job:" + jobId);
        ledger.save(e);
        return jobId;
    }

    @Test
    public void platformFailuresAreAutoRefundable() {
        Assert.assertTrue(RefundRequestService.platformFailure(
                RefundRequestService.classify("INTERRUPTED_UNCERTAIN", "Interrupted during browser work")));
        Assert.assertTrue(RefundRequestService.platformFailure(
                RefundRequestService.classify(null, "chrome not reachable")));
        Assert.assertFalse(RefundRequestService.platformFailure(
                RefundRequestService.classify("ALL_CASES_TODO", "No cases passed")));
        Assert.assertEquals(RefundRequestService.classify(null, "invalid credentials"), RetryCategory.CONFIG);
    }

    @Test
    public void approveReturnsCreditsOnceAndCapsAtCharge() {
        PortalUser u = user();
        String jobId = chargedJob(u, 25);
        Assert.assertEquals(credits.chargedForJob(jobId), 25L);
        long before = ((Number) credits.userCreditsDto(u.getId()).get("balance")).longValue();

        RefundRequest r = new RefundRequest();
        r.setJobId(jobId);
        r.setUserId(u.getId());
        r.setJobKind("EXECUTE");
        r.setChargedAmount(25);
        r.setFailureCategory("INFRA");
        r = requests.save(r);

        var out = refunds.approve(r.getId(), null, 20L, "partial");
        Assert.assertEquals(out.get("status"), "APPROVED");
        Assert.assertEquals(((Number) credits.userCreditsDto(u.getId()).get("balance")).longValue(), before + 20);

        // idempotent at the ledger: a second refund for the same job returns the first entry
        CreditLedgerEntry again = credits.refundForJob(u.getId(), jobId, "EXECUTE", 25, null, "dup");
        Assert.assertEquals(again.getDelta(), 20L);
        Assert.assertEquals(((Number) credits.userCreditsDto(u.getId()).get("balance")).longValue(), before + 20);

        Long id = r.getId();
        Assert.assertThrows(IllegalStateException.class, () -> refunds.reject(id, null, "too late"));
    }

    @Test
    public void cannotRefundMoreThanCharged() {
        PortalUser u = user();
        String jobId = chargedJob(u, 10);
        Assert.assertThrows(IllegalArgumentException.class,
                () -> credits.refundForJob(u.getId(), jobId, "EXECUTE", 11, null, "too much"));
    }

    @Test
    public void rejectNeedsAReason() {
        PortalUser u = user();
        String jobId = chargedJob(u, 5);
        RefundRequest r = new RefundRequest();
        r.setJobId(jobId);
        r.setUserId(u.getId());
        r.setChargedAmount(5);
        Long id = requests.save(r).getId();
        Assert.assertThrows(IllegalArgumentException.class, () -> refunds.reject(id, null, " "));
        Assert.assertEquals(refunds.reject(id, null, "Login credentials were wrong").get("status"), "REJECTED");
    }
}
