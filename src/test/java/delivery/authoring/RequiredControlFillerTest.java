package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RequiredControlFillerTest {

    @Test
    public void fillsEmptyFormInputsWithDummyValues() {
        String html = """
                <form>
                  <input data-test="firstName" name="firstName" value=""/>
                  <input data-test="postalCode" name="postalCode" value=""/>
                  <select data-test="country" name="country">
                    <option value="">Select</option>
                    <option value="US">United States</option>
                  </select>
                  <input type="radio" name="ship" data-test="ship-ground" value="ground"/>
                  <input type="radio" name="ship" data-test="ship-express" value="express"/>
                  <button type="submit">Continue</button>
                </form>
                """;
        List<ProvenStep> steps = RequiredControlFiller.planFillsBeforeClick(html, "TC1", "Click Continue");
        Assert.assertTrue(steps.size() >= 3, steps.toString());
        Assert.assertTrue(steps.stream().anyMatch(s ->
                "type".equals(s.action()) && "firstName".equals(s.locatorValue()) && !s.value().isBlank()));
        Assert.assertTrue(steps.stream().anyMatch(s ->
                "select".equals(s.action()) && "country".equals(s.locatorValue())));
        Assert.assertTrue(steps.stream().anyMatch(s ->
                "click".equals(s.action()) && s.rationale().contains("radio")));
    }

    @Test
    public void skipsAuthFields() {
        String html = """
                <form>
                  <input data-test="username" name="username" value=""/>
                  <input data-test="password" type="password" name="password" value=""/>
                  <input data-test="note" name="note" value=""/>
                </form>
                """;
        List<ProvenStep> steps = RequiredControlFiller.planFills(html, "TC2", false);
        Assert.assertTrue(steps.stream().noneMatch(s -> s.locatorValue().contains("user")));
        Assert.assertTrue(steps.stream().noneMatch(s -> s.locatorValue().contains("pass")));
        Assert.assertTrue(steps.stream().anyMatch(s -> "note".equals(s.locatorValue())));
    }

    @Test
    public void inventsZipAndEmailFromHints() {
        String zip = DummyValueInventor.invent("input", "text", "postalCode", "Zip", "");
        Assert.assertTrue(zip.matches("\\d{5}"), zip);
        String email = DummyValueInventor.invent("input", "email", "email", "", "");
        Assert.assertTrue(email.contains("@"), email);
    }

    @Test
    public void createPostDoesNotTriggerSubmitAutofill() {
        String html = """
                <form>
                  <input data-test="title" name="title" value=""/>
                </form>
                """;
        Assert.assertTrue(RequiredControlFiller.planFillsBeforeClick(
                html, "TC1", "Click Create post").isEmpty());
        Assert.assertFalse(RequiredControlFiller.planFillsBeforeClick(
                html, "TC1", "Click Create account").isEmpty());
    }
}
