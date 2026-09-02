package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HealLivenessFilterTest {

    @Test
    public void dropsZeroSizeBannerAndRetriesNamedButton() {
        String html = """
                <body>
                  <a href="/upstream">banner</a>
                  <button onclick="addItem()">Add Item</button>
                </body>
                """;
        AtomicInteger ollama = new AtomicInteger();
        LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "x") {
            @Override
            public String completeJson(String system, String user) {
                ollama.incrementAndGet();
                return "{\"candidateId\":\"c1\"}";
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                return completeJson(system, user);
            }
        };
        CandidateLivenessProbe probe = c -> {
            if (c.value() != null && c.value().contains("/upstream")) {
                return new CandidateLiveness(true, true, 0, 0);
            }
            return new CandidateLiveness(true, true, 80, 20);
        };
        HealCascade cascade = new HealCascade(
                new AuthoringService(llm, new LocatorValidator()),
                new CursorHealClient(false, "unused", 1));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Add Item button");
        HealResult result = cascade.heal(
                "TC", intent, html, null, "bind failed", null, true,
                List.of(), true, List.of(), List.of(), probe);
        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.tierUsed(), "retry");
        Assert.assertEquals(ollama.get(), 0);
        Assert.assertTrue(result.steps().get(0).locatorValue().toLowerCase().contains("add item"));
    }
}
