package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

public class GeneratedIdDetectorTest {

    @DataProvider(name = "generated")
    public Object[][] generated() {
        return new Object[][]{
                {"_r_15_"}, {"_r_k_"}, {":r0:"}, {":r1a:"},
                {"ember1423"}, {"ext-gen1024"}, {"gwt-uid-37"},
                {"mui-42"}, {"radix-:r3:"}, {"headlessui-menu-button-1"},
                {"svelte-1x2y3z"}, {"sc-bdVaJa"},
                {"u_0_1a"}, {"jsc_c_1a"}, {"mount_0_0_Hf"},
                {"a1b2c3d4"}, {"1234"}, {"x7f3"}, {"f47ac10b"},
                {"550e8400-e29b-41d4-a716-446655440000"},
                {"field_1234567"}, {"q"}
        };
    }

    @DataProvider(name = "authored")
    public Object[][] authored() {
        return new Object[][]{
                {"user-name"}, {"password"}, {"login-button"}, {"firstName"},
                {"postalCode"}, {"finish"}, {"continue"}, {"checkout"},
                {"add-to-cart-sauce-labs-backpack"}, {"item_4_title_link"},
                {"pwd"}, {"cvv"}, {"search-form"}, {"nav"}, {"flatpickr-day"},
                {"reg_email__"}, {"sex"}
        };
    }

    @Test(dataProvider = "generated")
    public void flagsFrameworkMintedIdentifiers(String id) {
        Assert.assertTrue(GeneratedIdDetector.looksGenerated(id), id + " should be treated as generated");
    }

    @Test(dataProvider = "authored")
    public void keepsHandWrittenIdentifiers(String id) {
        Assert.assertFalse(GeneratedIdDetector.looksGenerated(id), id + " should be treated as authored");
    }

    @Test
    public void generatedIdFallsBackToACssSelectorOnAHumanAttribute() {
        String html = """
                <form>
                  <input id="_r_15_" aria-label="First name" type="text">
                  <input id="_r_16_" aria-label="Surname" type="text">
                </form>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        Assert.assertTrue(candidates.stream().noneMatch(c -> "id".equals(c.strategy())),
                "generated ids must not be offered as locators: " + candidates);
        Assert.assertTrue(
                candidates.stream().anyMatch(c -> "css".equals(c.strategy())
                        && c.value().contains("First name")),
                "expected a CSS selector on the human attribute: " + candidates);
    }

    @Test
    public void authoredIdIsStillPreferredOverCss() {
        String html = "<form><input id=\"firstName\" aria-label=\"First name\" type=\"text\"></form>";
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        Assert.assertTrue(candidates.stream()
                        .anyMatch(c -> "id".equals(c.strategy()) && "firstName".equals(c.value())),
                candidates.toString());
    }

    @Test
    public void repeatedIdIdentifiesNothingSoItIsDropped() {
        String html = """
                <ul>
                  <li><button id="row-action" aria-label="Delete first row">x</button></li>
                  <li><button id="row-action" aria-label="Delete second row">x</button></li>
                </ul>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);

        Assert.assertTrue(candidates.stream().noneMatch(c -> "id".equals(c.strategy())),
                "a duplicated id cannot identify one element: " + candidates);
        Assert.assertTrue(candidates.stream().anyMatch(c -> "css".equals(c.strategy())),
                "expected a CSS fallback for the duplicated ids: " + candidates);
    }

    @Test
    public void validatorRefusesAGeneratedIdLocator() {
        LocatorValidator validator = new LocatorValidator();
        Assert.assertFalse(validator.validate(
                new LocatorCandidate("id", "_r_15_", "Page", "invented")).valid());
        Assert.assertTrue(validator.validate(
                new LocatorCandidate("id", "firstName", "Page", "invented")).valid());
    }
}
