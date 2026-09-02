package delivery.store;

import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class DomainLocatorMemoryTest {

    @Test
    public void remembersAndRecallsByHostPathAndIntent() {
        DomainLocatorMemory memory = new DomainLocatorMemory();
        StepIntentBinder.IntentLine firstName = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD, "Enter in the First name field");
        ProvenStep step = new ProvenStep(
                "TC1", "Reg", "elementAction", "type",
                "xpath", "//input[@id=//label[normalize-space(.)='First name']/@for]",
                "Jordan", "", "", true, "intent:TYPE_FIELD");
        memory.remember("web.example.com", "/reg/", firstName, step);
        Optional<ProvenStep> hit = memory.recall("web.example.com", "/reg/", firstName, "TC2");
        Assert.assertTrue(hit.isPresent());
        Assert.assertEquals(hit.orElseThrow().locatorValue(), step.locatorValue());
        Assert.assertTrue(memory.recall("web.example.com", "/login/", firstName, "TC2").isEmpty());
    }

    @Test
    public void forgetDropsAFailedSlot() {
        DomainLocatorMemory memory = new DomainLocatorMemory();
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Submit button");
        memory.remember("example.com", "/reg/", intent, new ProvenStep(
                "TC1", "Reg", "elementAction", "click",
                "css", "a[href='https://example.com/reg/']", "", "", "", true, "intent:CLICK"));
        memory.forget("example.com", "/reg/", intent);
        Assert.assertTrue(memory.recall("example.com", "/reg/", intent, "TC1").isEmpty());
    }

    @Test
    public void doesNotRememberANonSubmitNavigationForSubmit() {
        DomainLocatorMemory memory = new DomainLocatorMemory();
        StepIntentBinder.IntentLine submit = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Submit button");
        memory.remember("example.com", "/reg/", submit, new ProvenStep(
                "TC1", "Reg", "elementAction", "click",
                "css", "a[href='https://example.com/reg/']", "", "", "", true, "intent:CLICK"));
        Assert.assertTrue(memory.recall("example.com", "/reg/", submit, "TC1").isEmpty(),
                "self-path href is not a reusable Submit locator");
    }

    @Test
    public void roundTripsThroughJsonFile() throws Exception {
        DomainLocatorMemory memory = new DomainLocatorMemory();
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD, "Enter in the Surname field");
        memory.remember("example.com", "/reg/", intent, new ProvenStep(
                "TC1", "Reg", "elementAction", "type",
                "xpath", "//label[.='Surname']//input", "Lee", "", "", true, "intent:TYPE_FIELD"));
        Path file = Files.createTempFile("domain-loc-", ".json");
        try {
            memory.save(file);
            DomainLocatorMemory loaded = new DomainLocatorMemory();
            loaded.load(file);
            Assert.assertEquals(
                    loaded.recall("example.com", "/reg/", intent, "TC9").orElseThrow().locatorValue(),
                    "//label[.='Surname']//input");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
