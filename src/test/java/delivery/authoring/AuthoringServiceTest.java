package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class AuthoringServiceTest {
    private static final String HTML = """
            <body>
              <button data-test="login-button">Login</button>
              <a data-test="shopping-cart-link" href="#">Cart</a>
              <div data-test="inventory-container"></div>
            </body>
            """;

    @Test
    public void author_bindsCandidateIdOnly() throws Exception {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(HTML);
        DomCandidate cart = candidates.stream()
                .filter(c -> "shopping-cart-link".equals(c.value()))
                .findFirst()
                .orElseThrow();
        LocalLlmClient fake = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                return """
                        {"steps":[{"pageName":"Inventory","actionType":"elementAction","action":"click",
                        "candidateId":"%s","locatorStrategy":"","locatorValue":"","value":"",
                        "assertionType":"","assertionExpected":"","rationale":"ok"}]}
                        """.formatted(cart.id());
            }
        };
        AuthoringService service = new AuthoringService(fake, new LocatorValidator());
        ManualTestCase tc = new ManualTestCase("TC_001", "cart", "", "click cart", "ok", "", "");
        List<ProvenStep> steps = service.author(tc, HTML, null);
        Assert.assertEquals(steps.size(), 1);
        Assert.assertTrue(steps.get(0).validated());
        Assert.assertEquals(steps.get(0).locatorValue(), "shopping-cart-link");
    }

    @Test
    public void author_rejectsInventedLocator() throws Exception {
        LocalLlmClient fake = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                return """
                        {"steps":[{"pageName":"Inv","actionType":"elementAction","action":"click",
                        "candidateId":"c999","locatorStrategy":"data-test","locatorValue":"invented",
                        "value":"","assertionType":"","assertionExpected":"","rationale":"guess"}]}
                        """;
            }
        };
        AuthoringService service = new AuthoringService(fake, new LocatorValidator());
        ManualTestCase tc = new ManualTestCase("TC_002", "t", "", "click", "ok", "", "");
        List<ProvenStep> steps = service.author(tc, HTML, null);
        Assert.assertFalse(steps.get(0).validated());
    }

    @Test
    public void authorLoginPrelude_heuristicWhenLlmFails() throws Exception {
        String loginHtml = """
                <body>
                  <input data-test="username"/>
                  <input data-test="password"/>
                  <input data-test="login-button" type="submit"/>
                </body>
                """;
        LocalLlmClient fake = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                return """
                        {"steps":[{"pageName":"Login","actionType":"elementAction","action":"click",
                        "candidateId":"c999","value":"","assertionType":"","assertionExpected":"","rationale":"bad"}]}
                        """;
            }
        };
        AuthoringService service = new AuthoringService(fake, new LocatorValidator());
        ManualTestCase tc = new ManualTestCase("TC_L", "login", "", "login with user", "ok", "", "");
        List<ProvenStep> steps = service.authorLoginPrelude(tc, loginHtml);
        Assert.assertTrue(steps.size() >= 3);
        Assert.assertTrue(steps.stream().allMatch(ProvenStep::validated));
    }

    @Test
    public void forceTypeInventWhenExcelHasNoLiteral() {
        AuthoringService service = new AuthoringService(
                new LocalLlmClient("http://127.0.0.1:9", "dummy"), new LocatorValidator());
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "data-test", "firstName", "input", "First name"));
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD, "Enter a first name in the First name field");
        List<ProvenStep> forced = service.stepsPreferringCandidate("TC1", intent, candidates, "c1");
        Assert.assertTrue(forced.get(0).validated(), forced.get(0).rationale());
        Assert.assertEquals(forced.get(0).action(), "type");
        Assert.assertNotNull(forced.get(0).value());
        Assert.assertFalse(forced.get(0).value().isBlank());
        Assert.assertNotEquals(forced.get(0).value().toLowerCase(), "fname");
        Assert.assertFalse(forced.get(0).value().toLowerCase().contains("first name"));
    }
}
