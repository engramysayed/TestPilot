package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.store.LocatorOutcomeStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CodeWriterTest {
    @Test
    public void stripJavaComments_removesCommentsAndPreservesLiteralsWithSlashes() {
        String src = """
                /** page javadoc */
                public class Demo {
                    protected String url = "http://example.com/path"; // trailing
                    protected String xpath = "//button[@id='x']";
                    /* block
                       comment */
                    protected String single = "a // b";
                    void x() { /* inline */ y(); }
                }
                """;
        String out = CodeWriter.stripJavaComments(src);
        Assert.assertFalse(out.contains("javadoc"), out);
        Assert.assertFalse(out.contains("trailing"), out);
        Assert.assertFalse(out.contains("block"), out);
        Assert.assertFalse(out.contains("inline"), out);
        Assert.assertTrue(out.contains("http://example.com/path"), out);
        Assert.assertTrue(out.contains("//button[@id='x']"), out);
        Assert.assertTrue(out.contains("a // b"), out);
        Assert.assertTrue(out.contains("y();"), out);
    }

    @Test
    public void writesPassedAndTodoClasses() throws Exception {
        Path temp = Files.createTempDirectory("codegen");
        Path templateRoot = Path.of("customer-framework-template");
        CodeWriter writer = new CodeWriter(templateRoot.resolve("templates"));
        ProvenStep step = new ProvenStep("TC_001", "Login", "elementAction", "click",
                "css", "button[data-test='login-button']", "", "", "", true, "ok");
        List<TcOutcome> outcomes = List.of(
                new TcOutcome("TC_001", "Valid login", TcStatus.PASSED, List.of(step), "", null, false, List.of()),
                new TcOutcome("TC_002", TcStatus.TODO, List.of(), "failed", null)
        );
        writer.write(temp, outcomes);
        Assert.assertTrue(Files.exists(temp.resolve("src/test/java/project/tests/generated/Login_Test.java")));
        Assert.assertTrue(Files.exists(temp.resolve("src/test/java/project/tests/todo/TC_002TestTodo.java")));
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/Login_Page.java")));
        Assert.assertFalse(Files.exists(temp.resolve("src/main/java/project/pages/Login_Actions_Page.java")));
        Assert.assertFalse(Files.exists(temp.resolve("src/main/java/project/pages/Login_Locators_Page.java")));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/Login_Test.java"));
        Assert.assertTrue(java.contains("import project.pages.Login_Page;"), java);
        Assert.assertTrue(java.contains("private Login_Page loginPage;"), java);
        Assert.assertTrue(java.contains("loginPage = new Login_Page(driver)"), java);
        Assert.assertTrue(java.contains("@Test(description = \"TC_001 — Valid login\")"), java);
        Assert.assertTrue(java.contains("public void should_valid_login()"), java);
        Assert.assertFalse(java.contains("new project.pages.Login"));
        Assert.assertFalse(java.contains("typeType_"));
        Assert.assertFalse(java.contains("clickClick_"));
    }

    @Test
    public void writesLoginBeforeMethodWhenFlagged() throws Exception {
        Path temp = Files.createTempDirectory("codegen-login");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep loginType = new ProvenStep("TC_L", "PracticeTestLogin", "elementAction", "type",
                "id", "username", "${TARGET_USERNAME}", "", "", true, "ok");
        ProvenStep loginClick = new ProvenStep("TC_L", "PracticeTestLogin", "elementAction", "click",
                "id", "submit", "", "", "", true, "ok");
        ProvenStep body = new ProvenStep("TC_L", "LoggedInSuccessfully", "elementAction", "assert",
                "xpath", "//body//*[contains(normalize-space(.),'Logged In Successfully')]",
                "", "textContains", "Logged In Successfully", true, "intent:ASSERT_VISIBLE");
        TcOutcome outcome = new TcOutcome(
                "TC_L", "Login with valid credentials", TcStatus.PASSED, List.of(body), "", null,
                true, List.of(loginType, loginClick));
        writer.write(temp, List.of(outcome));
        String java = Files.readString(temp.resolve(
                "src/test/java/project/tests/generated/LoggedInSuccessfully_Test.java"));
        Assert.assertTrue(java.contains("username"), java);
        Assert.assertTrue(java.contains("PracticeTestLogin"), java);
        Assert.assertTrue(java.contains("assert_Logged_In_Successfully_Text_Contains")
                || java.contains("Logged_In_Successfully"), java);
        Assert.assertTrue(java.contains("\"Logged In Successfully\""), java);
        Assert.assertTrue(Files.exists(temp.resolve(
                "src/main/java/project/pages/LoggedInSuccessfully_Page.java")));
        Assert.assertTrue(Files.exists(temp.resolve(
                "src/main/java/project/pages/PracticeTestLogin_Page.java")));
        Assert.assertFalse(java.contains("assertTextContainsAssert___body"));
    }

    @Test
    public void writesPartialTodoWithProvenStepsAndStopComment() throws Exception {
        Path temp = Files.createTempDirectory("codegen-partial");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep ok = new ProvenStep("TC_P", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        ProvenStep typed = new ProvenStep("TC_P", "CheckoutStepOne", "elementAction", "type",
                "data-test", "firstName", "Test", "", "", true, "auto-fill:type");
        TcOutcome outcome = new TcOutcome(
                "TC_P", "Checkout flow", TcStatus.TODO, List.of(ok, typed),
                "No DOM candidate for intent CLICK: Click Finish", null, false, List.of());
        writer.write(temp, List.of(outcome));
        String java = Files.readString(temp.resolve(
                "src/test/java/project/tests/todo/CheckoutStepOne_TestTodo.java"));
        Assert.assertTrue(java.contains("STOPPED HERE"), java);
        Assert.assertTrue(java.contains("Click Finish"), java);
        Assert.assertFalse(java.contains("HEAL_EXHAUSTED"), java);
        Assert.assertTrue(java.contains("Cart_Page cartPage;") || java.contains("cartPage"), java);
        Assert.assertTrue(java.contains("TC_P — Checkout flow"), java);
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/Cart_Page.java")));
    }

    @Test
    public void pageVarNameEscapesJavaKeywords() {
        Assert.assertEquals(CodeWriter.pageVarName("New_Page"), "newPage");
        Assert.assertEquals(CodeWriter.pageVarName("NewPage"), "newPage");
        Assert.assertEquals(CodeWriter.pageVarName("Class_Page"), "classPage");
        Assert.assertEquals(CodeWriter.pageVarName("Login_Page"), "loginPage");
    }

    @Test
    public void writesKeywordPageStemWithoutIllegalVarName() throws Exception {
        Path temp = Files.createTempDirectory("codegen-keyword");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep step = new ProvenStep("TC_K", "New", "elementAction", "type",
                "css", "[data-axis-test-id='first-name-input']", "Ramy", "", "", true, "ok");
        writer.write(temp, List.of(new TcOutcome(
                "TC_K", "Create user", TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/New_Test.java"));
        Assert.assertTrue(java.contains("newPage = new New_Page(driver)"), java);
        Assert.assertFalse(java.contains("New new ="), java);
        Assert.assertTrue(java.contains("newPage.type_"), java);
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/New_Page.java")));
    }

    @Test
    public void emitsAssertsInChronologicalOrder() throws Exception {
        Path temp = Files.createTempDirectory("codegen-order");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep assertTitle = new ProvenStep("TC_O", "Inventory", "elementAction", "assert",
                "data-test", "title", "", "visible", "", true, "intent:ASSERT_VISIBLE");
        ProvenStep clickCart = new ProvenStep("TC_O", "Inventory", "elementAction", "click",
                "data-test", "shopping-cart-link", "", "", "", true, "intent:CLICK");
        ProvenStep assertHeader = new ProvenStep("TC_O", "Cart", "elementAction", "assert",
                "data-test", "cart-contents", "", "visible", "", true, "intent:ASSERT_VISIBLE");
        writer.write(temp, List.of(new TcOutcome(
                "TC_O", "Order", TcStatus.PASSED, List.of(assertTitle, clickCart, assertHeader),
                "", null, false, List.of())));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/Cart_Test.java"));
        int a = java.indexOf("assert_");
        int c = java.indexOf("click_");
        int a2 = java.lastIndexOf("assert_");
        Assert.assertTrue(a >= 0 && c > a && a2 > c, "expected assert, click, assert order in:\n" + java);
    }

    @Test
    public void textContainsUsesBodyGetTextContains() throws Exception {
        Path temp = Files.createTempDirectory("codegen-textcontains");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep bodyAssert = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "xpath", "//body", "", "textContains", "Welcome back", true, "intent:ASSERT");
        ProvenStep idTextAssert = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "id", "toast-msg", "", "textContains", "Created", true, "intent:ASSERT");
        ProvenStep checked = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "id", "agree", "", "checked", "", true, "intent:ASSERT");
        ProvenStep unknown = new ProvenStep("TC_T", "Home", "elementAction", "assert",
                "id", "x", "", "weirdType", "", true, "intent:ASSERT");
        writer.write(temp, List.of(new TcOutcome(
                "TC_T", "Text", TcStatus.PASSED, List.of(bodyAssert, idTextAssert, checked, unknown),
                "", null, false, List.of())));
        String actions = Files.readString(temp.resolve("src/main/java/project/pages/Home_Page.java"));
        Assert.assertTrue(actions.contains("textContains(welcome_Back_Lbl_Locator, expected)"), actions);
        Assert.assertTrue(actions.contains("elementSelected("), actions);
        Assert.assertTrue(actions.contains("public void assert_"), actions);
        Assert.assertFalse(actions.contains("return this;"), actions);
        Assert.assertTrue(actions.contains("unsupported assertionType: weirdType"), actions);
        Assert.assertTrue(actions.contains("softTrue(false"), actions);
    }

    @Test
    public void actionMethodsAreVoidNotFluent() throws Exception {
        Path temp = Files.createTempDirectory("codegen-void-actions");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep click = new ProvenStep("TC_V", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        writer.write(temp, List.of(new TcOutcome(
                "TC_V", "Click checkout", TcStatus.PASSED, List.of(click),
                "", null, false, List.of())));
        String actions = Files.readString(temp.resolve("src/main/java/project/pages/Cart_Page.java"));
        Assert.assertTrue(actions.contains("public void click_"), actions);
        Assert.assertFalse(actions.contains("return this;"), actions);
    }

    @Test
    public void emitsCallBeforeSetupInBeforeMethodFromFreshBrowser() throws Exception {
        Path temp = Files.createTempDirectory("codegen-call-before");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep addToCart = new ProvenStep("TC_CART", "Shop", "elementAction", "click",
                "id", "add-backpack", "", "", "", true, "intent:CLICK");
        ProvenStep checkoutBtn = new ProvenStep("TC_CHECKOUT", "Cart", "elementAction", "click",
                "id", "checkout", "", "", "", true, "intent:CLICK");
        TcOutcome checkout = new TcOutcome(
                "TC_CHECKOUT", "Checkout backpack", TcStatus.PASSED, List.of(checkoutBtn),
                "", null, false, List.of())
                .withSetup(List.of(addToCart));
        writer.write(temp, List.of(checkout));
        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/Cart_Test.java"));
        int prereq = java.indexOf("runPrerequisite_TC_CHECKOUT");
        int test = java.indexOf("public void should_checkout_backpack()");
        int setupCall = java.indexOf("click_Add_Backpack");
        int bodyCall = java.indexOf("click_Checkout");
        Assert.assertTrue(prereq >= 0 && test > prereq, java);
        Assert.assertTrue(setupCall > prereq && setupCall < test,
                "cart setup must run in @BeforeMethod, not depend on TestNG class order:\n" + java);
        Assert.assertTrue(bodyCall > test, java);
        Assert.assertTrue(java.contains("Shop"), java);
        Assert.assertTrue(Files.exists(temp.resolve("src/main/java/project/pages/Shop_Page.java")));
    }

    @Test
    public void reorderFieldsByStorePrimaryStaysButBagIsPermuted() throws Exception {
        // primary wins 10/10, slot 1 misses 5 never wins, slot 2 wins 3 misses 2.
        // Smoothed ratios: primary=11/12=0.917, slot1=1/7=0.143, slot2=4/7=0.571.
        // Expected order: [0, 2, 1] — primary stays, slots 1 and 2 swap in the bag.
        Path tmp = Files.createTempFile("outcomes", ".jsonl");
        StringBuilder jsonl = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            jsonl.append("{\"page\":\"Login_Page\",\"field\":\"loginBtn\",\"slot\":0,\"outcome\":\"WIN\",")
                    .append("\"target\":\"By.id: login\"}\n");
        }
        for (int i = 0; i < 5; i++) {
            jsonl.append("{\"page\":\"Login_Page\",\"field\":\"loginBtn\",\"slot\":1,\"outcome\":\"MISS\",")
                    .append("\"target\":\"By.cssSelector: #login\"}\n");
        }
        for (int i = 0; i < 3; i++) {
            jsonl.append("{\"page\":\"Login_Page\",\"field\":\"loginBtn\",\"slot\":2,\"outcome\":\"WIN\",")
                    .append("\"target\":\"By.xpath: //button\"}\n");
        }
        for (int i = 0; i < 2; i++) {
            jsonl.append("{\"page\":\"Login_Page\",\"field\":\"loginBtn\",\"slot\":2,\"outcome\":\"MISS\",")
                    .append("\"target\":\"By.xpath: //button\"}\n");
        }
        Files.writeString(tmp, jsonl.toString());
        LocatorOutcomeStore.Aggregate store = LocatorOutcomeStore.readFile(tmp);

        PageAccumulator.FieldModel f = new PageAccumulator.FieldModel(
                "loginBtn", "id", "login",
                List.of(), "", false,
                List.of(new MultiLocatorBag.Entry("cssSelector", "#login"),
                        new MultiLocatorBag.Entry("xpath", "//button")),
                "");
        List<PageAccumulator.FieldModel> out = CodeWriter.reorderFieldsByStore("Login_Page", List.of(f), store);

        Assert.assertEquals(out.size(), 1);
        PageAccumulator.FieldModel reordered = out.get(0);
        Assert.assertEquals(reordered.strategy(), "id", "primary strategy must stay");
        Assert.assertEquals(reordered.value(), "login", "primary value must stay");
        Assert.assertEquals(reordered.bag().size(), 2);
        Assert.assertEquals(reordered.bag().get(0).strategy(), "xpath",
                "slot 2 (xpath) should be first in the bag after reorder");
        Assert.assertEquals(reordered.bag().get(0).value(), "//button");
        Assert.assertEquals(reordered.bag().get(1).strategy(), "cssSelector",
                "slot 1 (css) should fall to the end");
        Assert.assertEquals(reordered.bag().get(1).value(), "#login");
    }

    @Test
    public void inlinedSetupKeepsPrereqTestDataAssertionsAndPageMethods() throws Exception {
        Path temp = Files.createTempDirectory("codegen-call-before-data");
        CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
        ProvenStep qty = new ProvenStep("TC_CART", "Shop", "elementAction", "type",
                "id", "qty", "2", "", "", true, "intent:TYPE");
        ProvenStep cartVisible = new ProvenStep("TC_CART", "Shop", "elementAction", "assert",
                "id", "cart-badge", "", "visible", "", true, "intent:ASSERT_VISIBLE");
        ProvenStep checkoutBtn = new ProvenStep("TC_CHECKOUT", "Cart", "elementAction", "click",
                "id", "checkout", "", "", "", true, "intent:CLICK");
        TcOutcome checkout = new TcOutcome(
                "TC_CHECKOUT", "Checkout backpack", TcStatus.PASSED, List.of(checkoutBtn),
                "", null, false, List.of())
                .withSetup(List.of(qty, cartVisible));
        writer.write(temp, List.of(checkout));

        String java = Files.readString(temp.resolve("src/test/java/project/tests/generated/Cart_Test.java"));
        int prereq = java.indexOf("runPrerequisite_TC_CHECKOUT");
        int test = java.indexOf("public void should_checkout_backpack()");
        int typeCall = java.indexOf("type_Qty");
        int assertCall = java.indexOf("assert_Cart_Badge_Is_Visible");
        Assert.assertTrue(prereq >= 0 && typeCall > prereq && typeCall < test, java);
        Assert.assertTrue(assertCall > prereq && assertCall < test, java);
        Assert.assertTrue(java.contains("PropertyReader.getProperty"), java);

        String data = Files.readString(temp.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertTrue(data.contains("TC_CART.body.type_Qty.1=2"), data);

        String actions = Files.readString(temp.resolve("src/main/java/project/pages/Shop_Page.java"));
        Assert.assertTrue(actions.contains("public void type_Qty"), actions);
    }
}
