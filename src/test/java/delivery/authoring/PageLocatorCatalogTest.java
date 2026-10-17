package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class PageLocatorCatalogTest {

    private static final String PAGE = """
            <html><body>
            <nav>
              <span data-axis-test-id="side-nav-support-submenu-button" aria-expanded="false">Support</span>
              <a data-axis-test-id="side-nav-bulk-charge-back-link">Bulk Charge Back</a>
            </nav>
            <h1>Home</h1>
            </body></html>
            """;

    private static final String AFTER_EXPAND = """
            <html><body>
            <nav>
              <span data-axis-test-id="side-nav-support-submenu-button" aria-expanded="false">Support</span>
              <a data-axis-test-id="side-nav-bulk-charge-back-link">Bulk Charge Back</a>
              <a data-axis-test-id="side-nav-kyc-link">Fancy Registered KYC</a>
            </nav>
            <h1>Home</h1>
            </body></html>
            """;

    @Test
    public void reusesCatalogUntilDomFingerprintChanges() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        List<DomCandidate> first = catalog.ensure("https://app.test/home", PAGE);
        Assert.assertFalse(first.isEmpty());
        int gen = catalog.generation();
        List<DomCandidate> same = catalog.ensure("https://app.test/home", PAGE);
        Assert.assertEquals(catalog.generation(), gen);
        Assert.assertEquals(same.size(), first.size());
        catalog.ensure("https://app.test/home", AFTER_EXPAND);
        Assert.assertTrue(catalog.generation() > gen, "submenu expand must rebuild");
        Assert.assertTrue(catalog.candidates().stream().anyMatch(c ->
                c.value().contains("side-nav-kyc-link") || c.label().toLowerCase().contains("kyc")));
    }

    @Test
    public void navigationRebuildsEvenIfHtmlLooksSimilar() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        catalog.ensure("https://app.test/home", PAGE);
        int gen = catalog.generation();
        catalog.ensure("https://app.test/bulk-charge-back", PAGE);
        Assert.assertTrue(catalog.generation() > gen);
    }

    @Test
    public void visionHitUsesStableCatalogLocatorWhenPresent() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        catalog.ensure("https://app.test/home", PAGE);
        DomCandidate visionGuess = new DomCandidate(
                "v1", "xpath", "//*[text()='Support']", "span", "Support");
        DomCandidate chosen = catalog.adoptOrPreferStable(visionGuess);
        Assert.assertTrue(
                chosen.value().contains("side-nav-support-submenu-button")
                        || "data-test".equals(chosen.strategy())
                        || chosen.strategy().equals("css") && chosen.value().contains("data-axis-test-id"),
                chosen.toString());
    }

    @Test
    public void visionHitIsAdoptedWhenCatalogDoesNotHaveIt() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        catalog.ensure("https://app.test/home", PAGE);
        int before = catalog.candidates().size();
        DomCandidate visionNew = new DomCandidate(
                "v2", "css", "button[data-axis-test-id='toast-close']", "button", "Close");
        DomCandidate chosen = catalog.adoptOrPreferStable(visionNew);
        Assert.assertEquals(chosen.value(), visionNew.value());
        Assert.assertEquals(catalog.candidates().size(), before, "adopt must not grow until proof");
        Assert.assertTrue(catalog.pendingCount() >= 1);
        catalog.commitAfterProof(visionNew);
        Assert.assertTrue(catalog.candidates().size() > before);
    }

    @Test
    public void classHashChurnDoesNotRebuild() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        catalog.ensure("https://app.test/home", PAGE);
        int gen = catalog.generation();
        String churn = PAGE.replace("<h1>Home</h1>", "<h1 class=\"css-xyz99\">Home</h1>");
        catalog.ensure("https://app.test/home", churn);
        Assert.assertEquals(catalog.generation(), gen);
    }

    @Test
    public void visionDoesNotGrowCatalogUntilProof() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        catalog.ensure("https://app.test/home", PAGE);
        int before = catalog.candidates().size();
        DomCandidate visionNew = new DomCandidate(
                "v2", "css", "button[data-axis-test-id='toast-close']", "button", "Close");
        catalog.adoptOrPreferStable(visionNew);
        Assert.assertEquals(catalog.candidates().size(), before);
        catalog.commitAfterProof(visionNew);
        Assert.assertTrue(catalog.candidates().size() > before);
    }

    @Test
    public void commitProvenSteps_growsCatalogOnlyAfterHelper() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        catalog.ensure("https://app.test/home", PAGE);
        int before = catalog.candidates().size();
        delivery.codegen.ProvenStep step = new delivery.codegen.ProvenStep(
                "TC", "Page", "elementAction", "click",
                "css", "button[data-axis-test-id='toast-close']", "",
                "", "", true, "proven", "");
        catalog.adoptOrPreferStable(new DomCandidate(
                "v", "css", "button[data-axis-test-id='toast-close']", "button", "Close"));
        Assert.assertEquals(catalog.candidates().size(), before);
        PageLocatorCatalog.commitProvenSteps(catalog, List.of(step));
        Assert.assertTrue(catalog.candidates().size() > before);
    }

    @Test
    public void adoptOrPreferStable_prefersTestIdOverRawId() {
        PageLocatorCatalog catalog = new PageLocatorCatalog();
        String html = """
                <html><body>
                <button id="btn-save" data-testid="save">Save</button>
                </body></html>
                """;
        catalog.ensure("https://app.test/form", html);
        DomCandidate visionById = new DomCandidate("v", "id", "btn-save", "button", "Save");
        DomCandidate chosen = catalog.adoptOrPreferStable(visionById);
        Assert.assertFalse(
                "id".equalsIgnoreCase(chosen.strategy()) && "btn-save".equals(chosen.value()),
                "preferStable must not keep raw id when testid exists: " + chosen);
        Assert.assertTrue(
                "data-testid".equalsIgnoreCase(chosen.strategy())
                        || chosen.value().contains("data-testid")
                        || "save".equals(chosen.value()),
                chosen.toString());
    }
}
