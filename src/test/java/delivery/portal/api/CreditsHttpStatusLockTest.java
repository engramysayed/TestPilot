package delivery.portal.api;

import delivery.portal.PortalApplication;
import delivery.portal.service.credits.InsufficientCreditsException;
import delivery.portal.service.credits.AccountApprovalException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.Map;

/** Maps exceptions → HTTP codes per QA lock (402 / 403). */
@SpringBootTest(classes = PortalApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "delivery.mail-enabled=false",
        "delivery.credits.enabled=true",
        "delivery.store-root=./target/test-delivery-store-credits-http",
        "delivery.work-dir=./target/test-delivery-work-credits-http"
})
@AutoConfigureMockMvc
public class CreditsHttpStatusLockTest extends AbstractTestNGSpringContextTests {

    @Autowired ApplicationContext ctx;

    @Test
    public void insufficientMapsTo402() {
        PortalExceptionHandler h = ctx.getBean(PortalExceptionHandler.class);
        InsufficientCreditsException ex = new InsufficientCreditsException(25L, 3L, "AUTOMATE");
        ResponseEntity<Map<String, Object>> res = h.insufficientCredits(ex);
        Assert.assertEquals(res.getStatusCode().value(), 402);
        Assert.assertEquals(res.getBody().get("code"), "INSUFFICIENT_CREDITS");
        Assert.assertEquals(res.getBody().get("required"), 25L);
        Assert.assertEquals(res.getBody().get("balance"), 3L);
        Assert.assertEquals(res.getBody().get("jobKind"), "AUTOMATE");
    }

    @Test
    public void accountNotApprovedMapsTo403() {
        PortalExceptionHandler h = ctx.getBean(PortalExceptionHandler.class);
        AccountApprovalException ex = new AccountApprovalException(
                AccountApprovalException.CODE_NOT_APPROVED, "PENDING_APPROVAL", "Account pending approval");
        ResponseEntity<Map<String, Object>> res = h.accountNotApproved(ex);
        Assert.assertEquals(res.getStatusCode().value(), 403);
        Assert.assertEquals(res.getBody().get("code"), "ACCOUNT_NOT_APPROVED");
        Assert.assertEquals(res.getBody().get("approvalStatus"), "PENDING_APPROVAL");
    }

    @Test
    public void accountLockedMapsTo403() {
        PortalExceptionHandler h = ctx.getBean(PortalExceptionHandler.class);
        AccountApprovalException ex = new AccountApprovalException(
                AccountApprovalException.CODE_LOCKED, "LOCKED", "Account locked");
        ResponseEntity<Map<String, Object>> res = h.accountNotApproved(ex);
        Assert.assertEquals(res.getStatusCode().value(), 403);
        Assert.assertEquals(res.getBody().get("code"), "ACCOUNT_LOCKED");
    }
}
