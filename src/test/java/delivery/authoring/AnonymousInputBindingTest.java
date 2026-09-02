package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Framework-generated ids ({@code _r_15_}) carry no meaning, so every field intent on such a page
 * scores the same control first. Binding must refuse instead of typing every value into one box.
 */
public class AnonymousInputBindingTest {

    private static final List<DomCandidate> ANONYMOUS_FORM = List.of(
            new DomCandidate("a1", "id", "_r_15_", "input", ""),
            new DomCandidate("a2", "id", "_r_16_", "input", ""),
            new DomCandidate("a3", "id", "_r_k_", "div", "")
    );

    private static final List<DomCandidate> LABELLED_FORM = List.of(
            new DomCandidate("b1", "data-test", "firstName", "input", "First name"),
            new DomCandidate("b2", "data-test", "lastName", "input", "Surname")
    );

    @Test
    public void refusesFieldIntentWhenNoControlCarriesTheFieldName() {
        ManualTestCase tc = new ManualTestCase(
                "TC_ANON", "Register", "",
                "1. Enter Test in the First name field",
                "Value accepted", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, ANONYMOUS_FORM);
        Assert.assertFalse(r.ok(),
                "Anonymous inputs must not absorb a named field intent: " + r.steps());
    }

    @Test
    public void stillBindsFieldIntentWhenTheControlIsNamed() {
        ManualTestCase tc = new ManualTestCase(
                "TC_NAMED", "Register", "",
                "1. Enter Test in the First name field",
                "Value accepted", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, LABELLED_FORM);
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorValue(), "firstName");
        Assert.assertNotEquals(r.steps().get(0).value(), "Test");
        Assert.assertFalse(r.steps().get(0).value().isBlank());
    }

    @Test
    public void surnameIntentDoesNotLandOnTheFirstNameControl() {
        ManualTestCase tc = new ManualTestCase(
                "TC_SURNAME", "Register", "",
                "1. Enter User in the Surname field",
                "Value accepted", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, LABELLED_FORM);
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorValue(), "lastName");
    }

    @Test
    public void valueBeforeFieldNameIsUsedVerbatimWhenConcrete() {
        Assert.assertNull(
                DummyValueInventor.extractExplicitValue("Enter Test in the First name field"));
        Assert.assertEquals(
                DummyValueInventor.extractExplicitValue(
                        "Enter test.user@example.com in the Mobile number or email address field"),
                "test.user@example.com");
    }

    @Test
    public void plainFieldMentionStillFallsBackToInventedValue() {
        Assert.assertNull(DummyValueInventor.extractExplicitValue("Enter the username"));
    }
}
