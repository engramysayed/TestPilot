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
    public void doesNotRememberOtpInputForVerifyOtpClick() {
        DomainLocatorMemory memory = new DomainLocatorMemory();
        StepIntentBinder.IntentLine verify = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Verify OTP button");
        memory.remember("opssit.axispay.app", "/login", verify, new ProvenStep(
                "TC1", "Login", "elementAction", "click",
                "id", "basic_otp", "", "", "", true, "intent:CLICK"));
        Assert.assertTrue(memory.recall("opssit.axispay.app", "/login", verify, "TC1").isEmpty(),
                "typed OTP field must not be recalled as the Verify OTP button");
    }

    @Test
    public void doesNotRememberUsernameFieldForSignInClick() {
        DomainLocatorMemory memory = new DomainLocatorMemory();
        StepIntentBinder.IntentLine signIn = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK_LOGIN, "Click the Sign in button");
        memory.remember("opssit.example.com", "/login", signIn, new ProvenStep(
                "TC1", "Login", "elementAction", "click",
                "id", "basic_login", "", "", "", true, "intent:CLICK_LOGIN"));
        Assert.assertTrue(memory.recall("opssit.example.com", "/login", signIn, "TC1").isEmpty(),
                "username field basic_login must not be recalled as Sign in");
    }

    @Test
    public void doesNotRememberLoginButtonForCartClick() {
        DomainLocatorMemory memory = new DomainLocatorMemory();
        StepIntentBinder.IntentLine addCart = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Add to cart button for backpack");
        memory.remember("www.saucedemo.com", "/inventory.html", addCart, new ProvenStep(
                "TC1", "Inv", "elementAction", "click",
                "id", "login-button", "", "", "", true, "intent:CLICK"));
        Assert.assertTrue(memory.recall("www.saucedemo.com", "/inventory.html", addCart, "TC1").isEmpty(),
                "login-button must not be remembered for add-to-cart");
    }

    @Test
    public void loadDropsPollutedLoginLocatorsForNonLoginIntents() throws Exception {
        Path file = Files.createTempFile("domain-loc-pollute-", ".json");
        try {
            Files.writeString(file, """
                    {"entries":[{
                      "host":"www.saucedemo.com","path":"/login",
                      "intentKey":"CLICK|click the add to cart button",
                      "strategy":"id","locator":"login-button","action":"click"
                    },{
                      "host":"www.saucedemo.com","path":"/login",
                      "intentKey":"CLICK_LOGIN|click the login button",
                      "strategy":"id","locator":"login-button","action":"click"
                    }]}
                    """);
            DomainLocatorMemory loaded = new DomainLocatorMemory();
            loaded.load(file);
            StepIntentBinder.IntentLine cart = new StepIntentBinder.IntentLine(
                    StepIntentBinder.IntentKind.CLICK, "Click the Add to cart button");
            StepIntentBinder.IntentLine login = new StepIntentBinder.IntentLine(
                    StepIntentBinder.IntentKind.CLICK_LOGIN, "Click the Login button");
            Assert.assertTrue(loaded.recall("www.saucedemo.com", "/login", cart, "TC1").isEmpty());
            Assert.assertTrue(loaded.recall("www.saucedemo.com", "/login", login, "TC1").isPresent());
        } finally {
            Files.deleteIfExists(file);
        }
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

    @Test
    public void sharedFileLivesOnTheDomainFolderNotTheProject() {
        Path store = Path.of("delivery-store");
        Path shared = DomainLocatorMemory.sharedFile(store, "https://opssit.axispay.app/login");
        Assert.assertEquals(shared, store.resolve("opssit-axispay-app").resolve("domain-locator-memory.json"));
        Assert.assertFalse(shared.toString().contains("prj_"));
    }

    @Test
    public void openSharedMergesPerProjectFilesThenPrefersTheDomainFile() throws Exception {
        Path store = Files.createTempDirectory("shared-mem");
        Path domain = store.resolve("opssit-axispay-app");
        Path prjA = domain.resolve("prj_aaa");
        Path prjB = domain.resolve("prj_bbb");
        Files.createDirectories(prjA);
        Files.createDirectories(prjB);
        Files.writeString(prjA.resolve("domain-locator-memory.json"), """
                {"entries":[{
                  "host":"opssit.axispay.app","path":"/login",
                  "intentKey":"TYPE_PASS|enter in the password field",
                  "strategy":"id","locator":"basic_password","action":"type"
                }]}
                """);
        Files.writeString(prjB.resolve("domain-locator-memory.json"), """
                {"entries":[{
                  "host":"opssit.axispay.app","path":"/login",
                  "intentKey":"TYPE_FIELD|enter the static otp 245345",
                  "strategy":"id","locator":"basic_otp","action":"type"
                }]}
                """);

        DomainLocatorMemory memory = new DomainLocatorMemory();
        Path shared = memory.openShared(store, "https://opssit.axispay.app");
        Assert.assertEquals(shared, domain.resolve("domain-locator-memory.json"));

        StepIntentBinder.IntentLine password = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_PASS, "Enter in the password field");
        StepIntentBinder.IntentLine otp = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.TYPE_FIELD, "Enter the static OTP 245345");
        Assert.assertEquals(memory.recall("opssit.axispay.app", "/login", password, "TC1")
                .orElseThrow().locatorValue(), "basic_password");
        Assert.assertEquals(memory.recall("opssit.axispay.app", "/login", otp, "TC1")
                .orElseThrow().locatorValue(), "basic_otp");
    }
}
