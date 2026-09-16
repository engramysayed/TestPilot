package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/** F13 / P1-04: downloaded tests must replay Call-before as explicit setup, not TestNG order. */
public class CallBeforeSetupTest {

    private static ProvenStep click(String tcId, String page, String control) {
        return new ProvenStep(tcId, page, "elementAction", "click",
                "id", control, "", "", "", true, "proven");
    }

    private static ProvenStep loginType(String tcId) {
        return new ProvenStep(tcId, "Login", "elementAction", "type",
                "id", "user", "${TARGET_USERNAME}", "", "", true, "login");
    }

    private static TcDraft draft(
            String id,
            TcDraftStatus status,
            List<ProvenStep> proven,
            List<ProvenStep> login,
            boolean needsLogin
    ) {
        return new TcDraft(id, id, "1. Click", "ok", status, proven, login,
                needsLogin, status == TcDraftStatus.PASSED ? -1 : 1,
                status == TcDraftStatus.TODO ? "Click" : "",
                status == TcDraftStatus.TODO ? "blocked" : "",
                status == TcDraftStatus.PASSED ? "evidence/" + id : "",
                0, "https://shop.example/cart");
    }

    private static ManualTestCase tc(String id, String callBefore) {
        return new ManualTestCase(id, id, "", "1. Go", "ok", "P1", "", "", "", "AUTOMATE", callBefore);
    }

    @Test
    public void checkoutSetupIncludesCartProvenStepsInOrder() {
        List<ManualTestCase> cases = List.of(tc("TC_CART", ""), tc("TC_CHECKOUT", "TC_CART"));
        ProvenStep addToCart = click("TC_CART", "Shop", "add-backpack");
        ProvenStep checkoutBtn = click("TC_CHECKOUT", "Cart", "checkout");
        List<TcDraft> drafts = List.of(
                draft("TC_CART", TcDraftStatus.PASSED, List.of(addToCart), List.of(), false),
                draft("TC_CHECKOUT", TcDraftStatus.PASSED, List.of(checkoutBtn), List.of(), false));

        List<ProvenStep> setup = CallBeforeSetup.stepsFor(
                drafts.get(1), drafts, cases);

        Assert.assertEquals(setup.size(), 1, "cart body must be checkout setup");
        Assert.assertEquals(setup.get(0).locatorValue(), "add-backpack");
        Assert.assertEquals(CallBeforeSetup.prerequisiteIds(cases, "TC_CHECKOUT"), List.of("TC_CART"));
    }

    @Test
    public void nestedPrerequisitesAreTransitive() {
        List<ManualTestCase> cases = List.of(
                tc("TC_LOGIN", ""), tc("TC_CART", "TC_LOGIN"), tc("TC_CHECKOUT", "TC_CART"));
        List<TcDraft> drafts = List.of(
                draft("TC_LOGIN", TcDraftStatus.PASSED, List.of(click("TC_LOGIN", "Home", "ok")), List.of(), false),
                draft("TC_CART", TcDraftStatus.PASSED, List.of(click("TC_CART", "Shop", "add")), List.of(), false),
                draft("TC_CHECKOUT", TcDraftStatus.PASSED, List.of(click("TC_CHECKOUT", "Cart", "go")), List.of(), false));

        List<ProvenStep> setup = CallBeforeSetup.stepsFor(drafts.get(2), drafts, cases);
        Assert.assertEquals(setup.stream().map(ProvenStep::tcId).toList(),
                List.of("TC_LOGIN", "TC_CART"));
    }

    @Test
    public void failedCartDemotesCheckoutWithClearReason() {
        List<ManualTestCase> cases = List.of(tc("TC_CART", ""), tc("TC_CHECKOUT", "TC_CART"));
        List<TcDraft> drafts = List.of(
                draft("TC_CART", TcDraftStatus.TODO, List.of(), List.of(), false),
                draft("TC_CHECKOUT", TcDraftStatus.PASSED,
                        List.of(click("TC_CHECKOUT", "Cart", "checkout")), List.of(), false));

        List<TcDraft> honest = CallBeforeSetup.applyHonesty(drafts, cases);
        TcDraft checkout = honest.stream().filter(d -> "TC_CHECKOUT".equals(d.tcId())).findFirst().orElseThrow();
        Assert.assertEquals(checkout.status(), TcDraftStatus.TODO);
        Assert.assertTrue(checkout.failureReason().startsWith(CallBeforeSetup.BLOCKED_PREFIX),
                checkout.failureReason());
        Assert.assertTrue(checkout.failureReason().contains("TC_CART"), checkout.failureReason());
        TcDraft cart = honest.stream().filter(d -> "TC_CART".equals(d.tcId())).findFirst().orElseThrow();
        Assert.assertEquals(cart.status(), TcDraftStatus.TODO, "prereq itself is unchanged");
    }

    @Test
    public void cycleThrowsActionableError() {
        List<ManualTestCase> cases = List.of(tc("TC_A", "TC_B"), tc("TC_B", "TC_A"));
        List<TcDraft> drafts = List.of(
                draft("TC_A", TcDraftStatus.PASSED, List.of(click("TC_A", "A", "a")), List.of(), false),
                draft("TC_B", TcDraftStatus.PASSED, List.of(click("TC_B", "B", "b")), List.of(), false));
        try {
            CallBeforeSetup.applyHonesty(drafts, cases);
            Assert.fail("expected CALL_BEFORE_CYCLE");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().startsWith("CALL_BEFORE_CYCLE:"), ex.getMessage());
        }
    }

    @Test
    public void skipsPrereqLoginWhenLeafAlreadyLogsIn() {
        List<ManualTestCase> cases = List.of(tc("TC_CART", ""), tc("TC_CHECKOUT", "TC_CART"));
        List<TcDraft> drafts = List.of(
                draft("TC_CART", TcDraftStatus.PASSED,
                        List.of(click("TC_CART", "Shop", "add")), List.of(loginType("TC_CART")), true),
                draft("TC_CHECKOUT", TcDraftStatus.PASSED,
                        List.of(click("TC_CHECKOUT", "Cart", "go")), List.of(loginType("TC_CHECKOUT")), true));

        List<ProvenStep> setup = CallBeforeSetup.stepsFor(drafts.get(1), drafts, cases);
        Assert.assertEquals(setup.size(), 1);
        Assert.assertEquals(setup.get(0).locatorValue(), "add");
        Assert.assertFalse(setup.stream().anyMatch(s -> "user".equals(s.locatorValue())));
    }

    @Test
    public void includesPrereqLoginWhenLeafHasNoLogin() {
        List<ManualTestCase> cases = List.of(tc("TC_CART", ""), tc("TC_CHECKOUT", "TC_CART"));
        List<TcDraft> drafts = List.of(
                draft("TC_CART", TcDraftStatus.PASSED,
                        List.of(click("TC_CART", "Shop", "add")), List.of(loginType("TC_CART")), true),
                draft("TC_CHECKOUT", TcDraftStatus.PASSED,
                        List.of(click("TC_CHECKOUT", "Cart", "go")), List.of(), false));

        List<ProvenStep> setup = CallBeforeSetup.stepsFor(drafts.get(1), drafts, cases);
        Assert.assertEquals(setup.stream().map(ProvenStep::locatorValue).toList(),
                List.of("user", "add"));
    }

    @Test
    public void sharedPrerequisiteRunsOncePerLeafInDependencyOrder() {
        // D → B,C; B → A; C → A  ⇒ A once, then B, then C (within-leaf first-seen wins)
        List<ManualTestCase> cases = List.of(
                tc("TC_A", ""),
                tc("TC_B", "TC_A"),
                tc("TC_C", "TC_A"),
                tc("TC_D", "TC_B,TC_C"));
        List<TcDraft> drafts = List.of(
                draft("TC_A", TcDraftStatus.PASSED, List.of(click("TC_A", "Home", "a")), List.of(), false),
                draft("TC_B", TcDraftStatus.PASSED, List.of(click("TC_B", "Shop", "b")), List.of(), false),
                draft("TC_C", TcDraftStatus.PASSED, List.of(click("TC_C", "Shop", "c")), List.of(), false),
                draft("TC_D", TcDraftStatus.PASSED, List.of(click("TC_D", "Cart", "d")), List.of(), false));

        Assert.assertEquals(CallBeforeSetup.prerequisiteIds(cases, "TC_D"),
                List.of("TC_A", "TC_B", "TC_C"));
        Assert.assertEquals(
                CallBeforeSetup.stepsFor(drafts.get(3), drafts, cases)
                        .stream().map(ProvenStep::locatorValue).toList(),
                List.of("a", "b", "c"));
    }

    @Test
    public void sharedPrerequisiteIsInlinedIndependentlyForEachLeaf() {
        List<ManualTestCase> cases = List.of(
                tc("TC_CART", ""), tc("TC_CHECKOUT", "TC_CART"), tc("TC_PAY", "TC_CART"));
        ProvenStep add = click("TC_CART", "Shop", "add");
        List<TcDraft> drafts = List.of(
                draft("TC_CART", TcDraftStatus.PASSED, List.of(add), List.of(), false),
                draft("TC_CHECKOUT", TcDraftStatus.PASSED, List.of(click("TC_CHECKOUT", "Cart", "go")), List.of(), false),
                draft("TC_PAY", TcDraftStatus.PASSED, List.of(click("TC_PAY", "Pay", "pay")), List.of(), false));

        Assert.assertEquals(CallBeforeSetup.stepsFor(drafts.get(1), drafts, cases), List.of(add));
        Assert.assertEquals(CallBeforeSetup.stepsFor(drafts.get(2), drafts, cases), List.of(add));
    }
}
