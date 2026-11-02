package delivery.portal.service.credits;

import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.roles.ApprovalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.UUID;

/**
 * Slice A: VISUAL_DIFF / SUITES_MONITOR / HEAL_REVIEW debit via tryDebitForAddon.
 */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "delivery.mail-enabled=false",
        "delivery.credits.enabled=true",
        "delivery.credits.cost.generate=10",
        "delivery.credits.cost.execute=5",
        "delivery.credits.cost.automate=25",
        "delivery.credits.cost.hunt=15",
        "spring.datasource.url=jdbc:h2:mem:addon-debit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "delivery.store-root=./target/test-delivery-store-addon-debit",
        "delivery.work-dir=./target/test-delivery-work-addon-debit"
})
public class AddonDebitTest extends AbstractTestNGSpringContextTests {

    @Autowired private CreditService credits;
    @Autowired private CreditCostTable costs;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private DeliveryCreditsProperties props;

    private PortalUser approved(String prefix) {
        PortalUser u = new PortalUser();
        u.setEmail(prefix + "-" + System.nanoTime() + "@testpilot.local");
        u.setPasswordHash(encoder.encode("AddonPass1!"));
        u.setRole(PortalUser.Role.USER);
        u.setEnabled(true);
        u.setApprovalStatus(ApprovalStatus.APPROVED);
        return users.save(u);
    }

    @Test
    public void addonSeedCostsMatchAdminDefaults() {
        Assert.assertEquals(costs.costByCode(CreditService.ADDON_VISUAL_DIFF), 5L);
        Assert.assertEquals(costs.costByCode(CreditService.ADDON_SUITES_MONITOR), 10L);
        Assert.assertEquals(costs.costByCode(CreditService.ADDON_HEAL_REVIEW), 5L);
    }

    @Test
    public void debitSuccessAndIdempotentForEachAddon() {
        PortalUser u = approved("addon-ok");
        credits.setCredits(u.getId(), "GRANT_DELTA", 100, "pack", "g-" + UUID.randomUUID(), null);
        long bal = 100L;

        String[] codes = {
                CreditService.ADDON_VISUAL_DIFF,
                CreditService.ADDON_SUITES_MONITOR,
                CreditService.ADDON_HEAL_REVIEW
        };
        long[] expect = {5L, 10L, 5L};
        for (int i = 0; i < codes.length; i++) {
            String stable = "ok-" + codes[i] + "-" + UUID.randomUUID();
            credits.tryDebitForAddon(u.getId(), codes[i], stable);
            bal -= expect[i];
            Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), bal, "after " + codes[i]);
            credits.tryDebitForAddon(u.getId(), codes[i], stable);
            Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), bal, "idempotent " + codes[i]);
        }
        Assert.assertEquals(bal, 100L - 5 - 10 - 5);
    }

    @Test
    public void shortBalanceRefusesEachAddon() {
        PortalUser u = approved("addon-poor");
        String[] codes = {
                CreditService.ADDON_VISUAL_DIFF,
                CreditService.ADDON_SUITES_MONITOR,
                CreditService.ADDON_HEAL_REVIEW
        };
        long[] required = {5L, 10L, 5L};
        for (int i = 0; i < codes.length; i++) {
            try {
                credits.tryDebitForAddon(u.getId(), codes[i], "poor-" + codes[i] + "-" + UUID.randomUUID());
                Assert.fail("expected InsufficientCreditsException for " + codes[i]);
            } catch (InsufficientCreditsException e) {
                Assert.assertEquals(e.required(), required[i], codes[i]);
                Assert.assertEquals(e.balance(), 0L, codes[i]);
                Assert.assertEquals(e.jobKind(), codes[i]);
            }
        }
        Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), 0L);
    }

    @Test
    public void disabledFlagSkipsAddonDebit() {
        boolean previous = props.isEnabled();
        try {
            props.setEnabled(false);
            PortalUser u = approved("addon-off");
            credits.tryDebitForAddon(u.getId(), CreditService.ADDON_VISUAL_DIFF, "off-" + UUID.randomUUID());
            Assert.assertEquals(credits.userCreditsDto(u.getId()).get("balance"), 0L);
        } finally {
            props.setEnabled(previous);
        }
    }
}
