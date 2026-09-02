package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class OpenPathAndValueExtractTest {
    @Test
    public void firstOpenPathPrefersAtPathOverSlashInTitle() {
        String steps = "1. Open the Add/Remove Elements page at /add_remove_elements/\n"
                + "2. Click the Add Element button";
        Assert.assertEquals(
                StepIntentBinder.firstOpenPath("", steps),
                "/add_remove_elements/");
    }

    @Test
    public void firstOpenPathReadsCheckboxes() {
        Assert.assertEquals(
                StepIntentBinder.firstOpenPath("", "1. Open the Checkboxes page at /checkboxes"),
                "/checkboxes");
    }

    @Test
    public void selectFromExtractsOptionValue() {
        Assert.assertEquals(
                DummyValueInventor.extractExplicitValue("Select Option 2 from the dropdown"),
                "Option 2");
    }

    @Test
    public void assertPhraseExtractsHelloWorld() {
        Assert.assertEquals(
                StepIntentBinder.extractAssertTextPhrase("Confirm the text Hello World! is visible"),
                "Hello World!");
    }

    @Test
    public void assertPhraseDoesNotGrabLandmarkPageShown() {
        Assert.assertNull(StepIntentBinder.extractAssertTextPhrase(
                "Confirm the Secure Area page is shown"));
    }

    @Test
    public void selectedStateNotTextPhrase() {
        Assert.assertNull(StepIntentBinder.extractAssertTextPhrase(
                "Confirm Option 2 is the selected value"));
        Assert.assertEquals(StepIntentBinder.extractStateAssertion(
                "Confirm Option 2 is the selected value"), "selected");
    }
}
