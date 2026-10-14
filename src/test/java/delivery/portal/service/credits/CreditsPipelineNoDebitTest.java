package delivery.portal.service.credits;

import delivery.portal.roles.ApprovalStatus;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * G-CR-04b soft check: with credits enabled and balance &gt; 0, no JOB_DEBIT appears until tryDebitForJob.
 * Full PipelineService POST coverage is follow-up IT; debit hook is only PortalStore.saveJob create.
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "delivery.mail-enabled=false",
        "delivery.credits.enabled=true",
        "delivery.store-root=./target/test-delivery-store-credits-pipe",
        "delivery.work-dir=./target/test-delivery-work-credits-pipe"
})
public class CreditsPipelineNoDebitTest extends AbstractTestNGSpringContextTests {

    @Autowired private CreditService credits;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;

    @Test
    public void noJobDebitWithoutTryDebitForJob() throws Exception {
        PortalUser u = new PortalUser();
        u.setEmail("pipe-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("PipePass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        users.save(u);
        credits.setCredits(u.getId(), "GRANT_DELTA", 50, "pipe pack", "pg-" + UUID.randomUUID(), null);
        Map<String, Object> usage = credits.usageForUser(u.getId(), null, null, null);
        long jobDebits = ((List<?>) usage.get("entries")).stream()
                .filter(e -> "JOB_DEBIT".equals(String.valueOf(((Map<?, ?>) e).get("reason"))))
                .count();
        Assert.assertEquals(jobDebits, 0L);
        Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), 50L);
    }
}
