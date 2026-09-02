package delivery.authoring;

import delivery.codegen.CodegenNaming;
import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * When the live locator is unlabeled (ordinal combobox), codegen still needs a stable field
 * name from the Excel intent — carried on the ProvenStep rationale as field=.
 */
public class IntentFieldRationaleTest {

    @Test
    public void extractsGenderFromSelectYourGenderWording() {
        Assert.assertEquals(
                StepIntentBinder.intentFieldPhrase(
                        "Select Female from the Select your gender dropdown"),
                "gender");
    }

    @Test
    public void extractsFirstNameFromEnterInFieldWording() {
        Assert.assertEquals(
                StepIntentBinder.intentFieldPhrase(
                        "Enter Test in the First name field"),
                "First name");
    }

    @Test
    public void typeFieldBindWritesFieldTokenOnRationale() {
        // Live node may have an accessible name while the proven locator is ordinal-only.
        List<DomCandidate> candidates = List.of(
                new DomCandidate("g", "xpath", "(//div[@role='combobox'])[4]",
                        "combobox", "Select your gender"));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD,
                "Select Female from the Select your gender dropdown",
                "Female");
        StepIntentBinder.BindResult r =
                StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        String rationale = r.steps().get(0).rationale().toLowerCase();
        Assert.assertTrue(rationale.contains("field=gender"),
                "expected field=gender on rationale, got: " + r.steps().get(0).rationale());
    }

    @Test
    public void codegenPrefersIntentFieldOverOrdinalCombobox() {
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "select",
                "xpath", "(//div[@role='combobox'])[4]", "Female", "", "", true,
                "intent:TYPE_FIELD:field=gender");
        Assert.assertEquals(CodegenNaming.actionMethodName(s), "select_Gender");
    }

    @Test
    public void fieldTokenStopsBeforeMemoryPipeSuffix() {
        ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "select",
                "xpath", "(//div[@role='combobox'])[4]", "Female", "", "", true,
                "memory:TYPE_FIELD:field=gender|select female from the select your gender dropdown");
        Assert.assertEquals(CodegenNaming.actionMethodName(s), "select_Gender");
    }
}
