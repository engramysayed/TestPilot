package delivery.store;

import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.testng.annotations.Test;
import java.nio.file.Files;
import static org.testng.Assert.*;

public class LocatorMemoryAssertionReuseTest {
    private static StepIntentBinder.IntentLine intent(String text) {
        return new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.ASSERT_VISIBLE, text);
    }
    private static void remember(DomainLocatorMemory memory, String instruction, String locator) {
        memory.remember("example.com", "/login", new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD, instruction), new ProvenStep("TC", "Page", "elementAction",
                "type", "css", locator, "SECRET", "", "", true, ""));
    }
    @Test public void persistedFieldLocatorReusedWithFreshAssertionSemantics() throws Exception {
        var memory = new DomainLocatorMemory();
        remember(memory, "Enter in the OTP field with '123456'", "input[data-test='otp']");
        var file = Files.createTempDirectory("assert-memory").resolve("memory.json");
        memory.save(file);
        var loaded = new DomainLocatorMemory(); loaded.load(file);
        for (String state : new String[]{"visible", "not visible"}) {
            var step = loaded.recall("example.com", "/login", intent("Confirm the OTP field is " + state), "NEW").orElseThrow();
            assertEquals(step.action(), "assert");
            assertEquals(step.assertionType(), state.equals("visible") ? "visible" : "notVisible");
            assertEquals(step.locatorValue(), "input[data-test='otp']");
            assertEquals(step.tcId(), "NEW"); assertEquals(step.value(), "");
        }
        assertFalse(Files.readString(file).contains("SECRET"));
        assertTrue(loaded.recall("other.example", "/login", intent("Confirm the OTP field is visible"), "TC").isEmpty());
        assertTrue(loaded.recall("example.com", "/other", intent("Confirm the OTP field is visible"), "TC").isEmpty());
        assertTrue(loaded.recall("example.com", "/login", intent("Confirm the OTP field is visible and Dashboard is shown"), "TC").isEmpty());
    }
    @Test public void conflictingFieldTargetsRejectReuse() {
        var memory = new DomainLocatorMemory();
        remember(memory, "Enter in the OTP field", "#first");
        remember(memory, "Enter in the OTP field with '123456'", "#second");
        assertTrue(memory.recall("example.com", "/login", intent("Confirm the OTP field is visible"), "TC").isEmpty());
    }
    @Test public void legacyAndTaggedInputAliasesPreferNarrowSelector() {
        var memory = new DomainLocatorMemory();
        remember(memory, "Enter in the OTP field", "input[data-test='otp']");
        remember(memory, "Enter in the OTP field with '123456'", "[data-test='otp']");
        assertEquals(memory.recall("example.com", "/login", intent("Confirm the OTP field is visible"), "TC").orElseThrow().locatorValue(), "input[data-test='otp']");
    }
}
