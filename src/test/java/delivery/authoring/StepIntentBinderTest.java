package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Binder tests use generic shop-like DOM tokens — not a specific public site.
 */
public class StepIntentBinderTest {

    private static final List<DomCandidate> CATALOG = List.of(
            new DomCandidate("c1", "data-test", "product-grid", "div", "products"),
            new DomCandidate("c2", "data-test", "shopping-cart-link", "a", "cart"),
            new DomCandidate("c3", "data-test", "add-to-cart-red-backpack", "button", "Add Red Backpack"),
            new DomCandidate("c4", "data-test", "checkout", "button", "Checkout"),
            new DomCandidate("c5", "data-test", "item_lantern_title_link", "a", "Camping Lantern")
    );

    @Test
    public void assertVisible_notClick() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "Catalog", "Already logged in",
                "1. Confirm products are visible on the catalog page",
                "Products shown", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, CATALOG);
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().size(), 1);
        Assert.assertEquals(r.steps().get(0).action(), "assert");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("product")
                || r.steps().get(0).locatorValue().contains("grid"));
    }

    @Test
    public void addNamedProduct_notCheckout() {
        ManualTestCase tc = new ManualTestCase(
                "TC2", "Add", "",
                "1. Add the red backpack to the cart",
                "Backpack in cart", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, CATALOG);
        Assert.assertTrue(r.ok(), r.rejectReason());
        ProvenStep step = r.steps().get(0);
        Assert.assertEquals(step.action(), "click");
        Assert.assertEquals(step.rationale(), "intent:CLICK");
        Assert.assertTrue(step.locatorValue().contains("backpack"));
        Assert.assertFalse(step.locatorValue().contains("checkout"));
    }

    @Test
    public void clickCart_bindsCartLink() {
        ManualTestCase tc = new ManualTestCase(
                "TC3", "Cart", "",
                "1. Click the cart",
                "Cart page", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, CATALOG);
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorValue(), "shopping-cart-link");
    }

    @Test
    public void rejectsAddBoundOnlyToCheckout() {
        List<DomCandidate> onlyCheckout = List.of(
                new DomCandidate("c4", "data-test", "checkout", "button", "Checkout")
        );
        ManualTestCase tc = new ManualTestCase(
                "TC4", "Add", "",
                "1. Add backpack to cart",
                "ok", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, onlyCheckout);
        Assert.assertFalse(r.ok());
    }

    @Test
    public void confirmCartItem_isAssertNotAdd() {
        ManualTestCase tc = new ManualTestCase(
                "TC4b", "Confirm", "",
                "1. Add Red Backpack to the cart\n2. Confirm the cart shows that an item was added",
                "Backpack is in the cart", "P2", "");
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertEquals(intents.get(0).kind(), StepIntentBinder.IntentKind.CLICK);
        Assert.assertEquals(intents.get(1).kind(), StepIntentBinder.IntentKind.ASSERT_VISIBLE);
        List<DomCandidate> withBadge = List.of(
                new DomCandidate("c1", "data-test", "add-to-cart-red-backpack", "button", "Add"),
                new DomCandidate("c2", "data-test", "shopping-cart-badge", "span", "1"),
                new DomCandidate("c3", "data-test", "add-to-cart-camping-lantern", "button", "Add")
        );
        StepIntentBinder.BindResult assertBind = StepIntentBinder.bindSingle(
                intents.get(1), "TC4b", withBadge, List.of());
        Assert.assertTrue(assertBind.ok(), assertBind.rejectReason());
        Assert.assertEquals(assertBind.steps().get(0).action(), "assert");
        Assert.assertTrue(assertBind.steps().get(0).locatorValue().contains("badge")
                || assertBind.steps().get(0).locatorValue().contains("cart"));
    }

    @Test
    public void openNamedProduct_notAddToCart() {
        ManualTestCase tc = new ManualTestCase(
                "TC5", "Open", "",
                "1. Open the Camping Lantern product",
                "details", "P2", "");
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "data-test", "add-to-cart-camping-lantern", "button", "Add"),
                new DomCandidate("c2", "data-test", "item_lantern_title_link", "a", "Camping Lantern"),
                new DomCandidate("c3", "data-test", "camping-lantern-img", "img", "Camping Lantern")
        );
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, candidates);
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).action(), "click");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("title_link")
                        || r.steps().get(0).locatorValue().contains("lantern"),
                r.steps().get(0).locatorValue());
        Assert.assertFalse(r.steps().get(0).locatorValue().startsWith("add-to-cart"));
    }

    @Test
    public void bindsCssWhenOnlyAriaLabelPresent() {
        List<DomCandidate> cssOnly = List.of(
                new DomCandidate("c1", "css", "button[aria-label='Add red backpack to cart']",
                        "button", "Add red backpack to cart"),
                new DomCandidate("c2", "css", "a[title='Shopping cart']", "a", "Shopping cart")
        );
        ManualTestCase tc = new ManualTestCase(
                "TC6", "Add", "",
                "1. Add the red backpack to the cart",
                "ok", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, cssOnly);
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorStrategy(), "css");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("aria-label"));
    }

    @Test
    public void prefersDataTestOverCssWhenBothMatch() {
        List<DomCandidate> mixed = List.of(
                new DomCandidate("c1", "css", "button[aria-label='cart']", "button", "cart"),
                new DomCandidate("c2", "data-test", "shopping-cart-link", "a", "cart")
        );
        ManualTestCase tc = new ManualTestCase(
                "TC7", "Cart", "",
                "1. Click the cart",
                "ok", "P1", "");
        StepIntentBinder.BindResult r = StepIntentBinder.bind(tc, mixed);
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).locatorStrategy(), "data-test");
        Assert.assertEquals(r.steps().get(0).locatorValue(), "shopping-cart-link");
    }

    @Test
    public void skipsOpenLoginPageNavigation() {
        ManualTestCase tc = new ManualTestCase(
                "E2E", "Checkout", "",
                """
                        1. Open the application login page
                        2. Enter the username demo_user
                        3. Enter the password demo_pass
                        4. Click the Login button
                        5. Confirm the Products page is shown
                        6. Add Red Backpack to the cart
                        """,
                "ok", "P1", "");
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertTrue(intents.stream().noneMatch(i ->
                i.text().toLowerCase().contains("open the application login")), intents.toString());
        Assert.assertTrue(intents.stream().anyMatch(i ->
                i.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE));
        Assert.assertTrue(intents.stream().anyMatch(i ->
                i.kind() == StepIntentBinder.IntentKind.CLICK
                        && i.text().toLowerCase().contains("backpack")));
    }

    @Test
    public void typeField_inventsDummyForShippingName() {
        List<DomCandidate> form = List.of(
                new DomCandidate("c1", "data-test", "firstName", "input", "First Name"),
                new DomCandidate("c2", "data-test", "lastName", "input", "Last Name"),
                new DomCandidate("c3", "data-test", "postalCode", "input", "Zip/Postal Code")
        );
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD, "Enter first name");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC_F", form, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).action(), "type");
        Assert.assertEquals(r.steps().get(0).locatorValue(), "firstName");
        Assert.assertFalse(r.steps().get(0).value().isBlank());
    }

    @Test
    public void classifyEnterPostalAsTypeField() {
        ManualTestCase tc = new ManualTestCase(
                "TC_Z", "Ship", "",
                "1. Enter postal code\n2. Click Continue",
                "ok", "P1", "");
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertEquals(intents.get(0).kind(), StepIntentBinder.IntentKind.TYPE_FIELD);
        Assert.assertEquals(intents.get(1).kind(), StepIntentBinder.IntentKind.CLICK);
    }

    @Test
    public void addNamedEntities_doesNotSwapAcrossCatalog() {
        List<DomCandidate> inventory = List.of(
                new DomCandidate("c1", "id", "add-red-backpack", "button", "Add"),
                new DomCandidate("c2", "id", "add-bike-light", "button", "Add"),
                new DomCandidate("c3", "id", "add-bolt-t-shirt", "button", "Add"),
                new DomCandidate("c4", "data-test", "tray-link", "a", "tray")
        );
        StepIntentBinder.BindResult backpack = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,
                        "Add the red backpack"),
                "E2E", inventory, List.of());
        Assert.assertTrue(backpack.ok(), backpack.rejectReason());
        Assert.assertTrue(backpack.steps().get(0).locatorValue().contains("backpack"),
                backpack.steps().get(0).locatorValue());

        StepIntentBinder.BindResult bike = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,
                        "Add the bike light"),
                "E2E", inventory, List.of());
        Assert.assertTrue(bike.ok(), bike.rejectReason());
        Assert.assertTrue(bike.steps().get(0).locatorValue().contains("bike-light")
                        || bike.steps().get(0).locatorValue().contains("bike_light"),
                bike.steps().get(0).locatorValue());
        Assert.assertFalse(bike.steps().get(0).locatorValue().contains("bolt"));
        Assert.assertFalse(bike.steps().get(0).locatorValue().contains("backpack"));
    }

    @Test
    public void addNamedEntity_rejectsWhenOnlyOtherEntitiesPresent() {
        List<DomCandidate> withoutBackpack = List.of(
                new DomCandidate("c2", "id", "add-bike-light", "button", "Add"),
                new DomCandidate("c3", "id", "add-bolt-t-shirt", "button", "Add")
        );
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,
                        "Add the red backpack"),
                "E2E", withoutBackpack, List.of());
        Assert.assertFalse(r.ok(), "must not bind named entity to unrelated controls");
    }

    @Test
    public void retainDistinctiveMatches_keepsOnlyMatchingProduct() {
        List<DomCandidate> inventory = List.of(
                new DomCandidate("c1", "id", "add-to-cart-red-backpack", "button", "Add to cart"),
                new DomCandidate("c2", "id", "add-to-cart-blue-lantern", "button", "Add to cart"),
                new DomCandidate("c3", "id", "add-to-cart-bolt-t-shirt", "button", "Add to cart")
        );
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add the red backpack to the cart");
        List<DomCandidate> kept = StepIntentBinder.retainDistinctiveMatches(intent, inventory);
        Assert.assertEquals(kept.size(), 1);
        Assert.assertTrue(kept.get(0).value().contains("backpack"));
    }

    @Test
    public void namedMultiWordEntity_rejectsSiblingWithSharedTokens() {
        List<DomCandidate> inventory = List.of(
                new DomCandidate("c1", "id", "add-red-backpack", "button", "Add"),
                new DomCandidate("c2", "id", "add-bike-light", "button", "Add"),
                new DomCandidate("c3", "id", "add-bolt-t-shirt", "button", "Add")
        );
        Assert.assertFalse(StepIntentBinder.candidateCarriesDistinctiveTokens(
                "Add the bike light", inventory.get(2)));
        Assert.assertTrue(StepIntentBinder.candidateCarriesDistinctiveTokens(
                "Add the bike light", inventory.get(1)));

        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,
                        "Add the bike light"),
                "E2E", List.of(inventory.get(0), inventory.get(2)), List.of());
        Assert.assertFalse(r.ok(), "must not bind bike light to sibling controls when bike-light absent");
    }

    @Test
    public void namedAction_prefersActionControlOverTitleLink() {
        List<DomCandidate> inventory = List.of(
                new DomCandidate("c1", "data-test", "entity-title", "a", "Red Backpack"),
                new DomCandidate("c2", "id", "add-red-backpack", "button", "Add"),
                new DomCandidate("c3", "id", "red-backpack-img", "img", "Red Backpack")
        );
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK,
                        "Add the red backpack"),
                "E2E", inventory, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("add"),
                r.steps().get(0).locatorValue());
    }

    @Test
    public void retainDistinctiveMatches_filtersShortMultiWordEntity() {
        List<DomCandidate> inventory = List.of(
                new DomCandidate("c1", "id", "add-red-backpack", "button", "Add"),
                new DomCandidate("c2", "id", "add-bike-light", "button", "Add"),
                new DomCandidate("c3", "id", "add-bolt-t-shirt", "button", "Add")
        );
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add the bike light");
        List<DomCandidate> kept = StepIntentBinder.retainDistinctiveMatches(intent, inventory);
        Assert.assertEquals(kept.size(), 1, kept.toString());
        Assert.assertTrue(kept.get(0).value().contains("bike-light"));
    }

    @Test
    public void stepsPreferringCandidate_rejectsForcedWrongMultiWordEntity() {
        List<DomCandidate> withoutBikeLight = List.of(
                new DomCandidate("c1", "id", "add-red-backpack", "button", "Add"),
                new DomCandidate("c3", "id", "add-bolt-t-shirt", "button", "Add")
        );
        AuthoringService authoring = new AuthoringService(
                new LocalLlmClient("http://127.0.0.1:9", "dummy"), new LocatorValidator());
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add the bike light");
        List<ProvenStep> forced = authoring.stepsPreferringCandidate("E2E", intent, withoutBikeLight, "c3");
        Assert.assertFalse(forced.get(0).validated(),
                "heal must not force wrong sibling for named entity: " + forced.get(0).rationale());
    }

    @Test
    public void retainDistinctiveMatches_namedActionDoesNotFallBackToTitle() {
        List<DomCandidate> inventory = List.of(
                new DomCandidate("c1", "data-test", "entity-title", "a", "Red Backpack"),
                new DomCandidate("c2", "id", "red-backpack-img", "img", "Red Backpack")
        );
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add the red backpack");
        List<DomCandidate> kept = StepIntentBinder.retainDistinctiveMatches(intent, inventory);
        Assert.assertTrue(kept.isEmpty(), "must not offer title/image when no action control matches");
    }

    @Test
    public void stepsPreferringCandidate_rejectsForcedWrongProduct() {
        List<DomCandidate> withoutBackpack = List.of(
                new DomCandidate("c2", "id", "add-blue-lantern", "button", "Add"),
                new DomCandidate("c3", "id", "add-bolt-t-shirt", "button", "Add")
        );
        AuthoringService authoring = new AuthoringService(
                new LocalLlmClient("http://127.0.0.1:9", "dummy"), new LocatorValidator());
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add the red backpack");
        List<ProvenStep> forced = authoring.stepsPreferringCandidate("E2E", intent, withoutBackpack, "c2");
        Assert.assertFalse(forced.get(0).validated(),
                "heal force-path must not validate wrong named entity: " + forced.get(0).rationale());
    }

    @Test
    public void submitWordingIsAFormClickNotAuthLogin() {
        Assert.assertEquals(
                StepIntentBinder.classify("click the submit button"),
                StepIntentBinder.IntentKind.CLICK);
        Assert.assertEquals(
                StepIntentBinder.classify("click the login button"),
                StepIntentBinder.IntentKind.CLICK_LOGIN);
    }

    @Test
    public void submitClickDoesNotBindCreatePost() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css", "a[href='/create']", "a", "Create post"),
                new DomCandidate("c2", "css", "button[name='websubmit']", "button", "Sign Up"));
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Click the Submit button"),
                "TC1", candidates, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertFalse(result.steps().get(0).locatorValue().contains("/create"),
                result.steps().get(0).locatorValue());
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("websubmit"),
                result.steps().get(0).locatorValue());
    }

    @Test
    public void loginBindHonorsVisionPreferredCandidate() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "header-login", "a", "Log in"),
                new DomCandidate("c2", "id", "form-login", "button", "Log in"));
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK_LOGIN, "Click the Login button"),
                "TC1", candidates, List.of("c2"));
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertEquals(result.steps().get(0).locatorValue(), "form-login");
    }

    @Test
    public void submitClickDoesNotBindALoginHref() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css", "a[href='https://web.example.com/login/']", "a", "Log in"),
                new DomCandidate("c2", "css", "button[name='websubmit']", "button", "Sign Up"));
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK_LOGIN, "Click the Submit button"),
                "TC1", candidates, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertFalse(result.steps().get(0).locatorValue().contains("/login"),
                "Submit must not click a login link, got " + result.steps().get(0).locatorValue());
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("websubmit")
                        || result.steps().get(0).locatorValue().toLowerCase().contains("sign"),
                result.steps().get(0).locatorValue());
    }

    @Test
    public void submitClickDoesNotBindAlreadyHaveAnAccountOrSelfPathHref() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css",
                        "a[href='https://web.example.com/reg/']", "a", "I already have an account"),
                new DomCandidate("c2", "css", "a[href='https://web.example.com/reg/']", "a", "Home"),
                new DomCandidate("c3", "css", "button[name='websubmit']", "button", "Sign Up"));
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Click the Submit button"),
                "TC1", candidates, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("websubmit"),
                "Submit must bind Sign Up, got " + result.steps().get(0).locatorValue());
    }

    @Test
    public void submitClickDoesNotBindCreateNewAccountHrefToRegPath() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css",
                        "a[href='https://web.example.com/reg/']", "a", "Create new account"),
                new DomCandidate("c2", "css", "button[name='websubmit']", "button", "Sign Up"));
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Click the Submit button"),
                "TC1", candidates, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertFalse(result.steps().get(0).locatorValue().contains("/reg/"),
                "Submit must not click a /reg/ href, got " + result.steps().get(0).locatorValue());
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("websubmit"),
                result.steps().get(0).locatorValue());
    }

    @Test
    public void submitClickRefusesWhenOnlyRegisterPathAnchorsExist() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css",
                        "a[href='https://web.example.com/reg/']", "a", "Create new account"));
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Click the Submit button"),
                "TC1", candidates, List.of());
        Assert.assertFalse(result.ok(), "Submit must not bind a /reg/ anchor: " + result.steps());
    }

    @Test
    public void testDataColumnFillsTypeWhenStepHasNoLiteral() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "reg", "Open at /reg/",
                "1. Open the form at /reg/\n2. Enter in the First name field",
                "ok", "", "", "", "\nAlice");
        List<StepIntentBinder.IntentLine> body = StepIntentBinder.bodyIntents(tc, false);
        StepIntentBinder.IntentLine enter = body.stream()
                .filter(i -> i.kind() == StepIntentBinder.IntentKind.TYPE_FIELD)
                .findFirst()
                .orElseThrow();
        Assert.assertEquals(enter.testData(), "Alice");
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css", "input[name='firstname']", "input", "First name"));
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(enter, "TC1", candidates, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertEquals(result.steps().get(0).value(), "Alice");
    }

    @Test
    public void leaveEmptyStep_clearsFieldAndIgnoresMisalignedTestData() {
        List<DomCandidate> form = List.of(
                new DomCandidate("c1", "css", "input[name='email']", "input", "Email or phone"),
                new DomCandidate("c2", "css", "input[name='pass']", "input", "Password"));
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD,
                "Leave the Email or phone field empty",
                "<VALID_PASSWORD>");
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(intent, "TC1", form, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertEquals(result.steps().get(0).action(), "clear");
        Assert.assertEquals(result.steps().get(0).value(), "");
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("email"));
    }

    @Test
    public void leaveEmptyTypeUser_clearsAndIgnoresTestData() {
        List<DomCandidate> form = List.of(
                new DomCandidate("c1", "css", "input[name='email']", "input", "Email"),
                new DomCandidate("c2", "css", "input[name='pass']", "input", "Password"));
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_USER,
                "Leave the email field empty",
                "should-not-type");
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(intent, "TC1", form, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertEquals(result.steps().get(0).action(), "clear");
        Assert.assertEquals(result.steps().get(0).value(), "");
    }

    @Test
    public void leaveEmptyTypePass_clearsAndIgnoresTestData() {
        List<DomCandidate> form = List.of(
                new DomCandidate("c1", "css", "input[name='email']", "input", "Email"),
                new DomCandidate("c2", "css", "input[name='pass']", "input", "Password"));
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_PASS,
                "Leave the password field empty",
                "Secret1!");
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(intent, "TC1", form, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertEquals(result.steps().get(0).action(), "clear");
        Assert.assertEquals(result.steps().get(0).value(), "");
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("pass"));
    }

    @Test
    public void leaveBlankPhrasing_clearsField() {
        List<DomCandidate> form = List.of(
                new DomCandidate("c1", "css", "input[name='email']", "input", "Email"));
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD,
                "Leave the email field blank",
                "nope");
        StepIntentBinder.BindResult result = StepIntentBinder.bindSingle(intent, "TC1", form, List.of());
        Assert.assertTrue(result.ok(), result.rejectReason());
        Assert.assertEquals(result.steps().get(0).action(), "clear");
    }
}
