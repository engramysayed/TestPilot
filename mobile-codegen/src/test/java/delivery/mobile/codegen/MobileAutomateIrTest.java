package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertTrue;

public class MobileAutomateIrTest {

    @Test
    public void noPhoneWritesTextShells() throws Exception {
        MobileCaseIr ir = MobileAutomateIr.build(
                "TC_01", "com.app",
                List.of("Tap the \"Login\" button"),
                List.of(), List.of(), List.of());
        String java = emit(ir);
        assertTrue(java.contains("UiSelector().text(\\\"Login\\\")"), java);
        assertTrue(ir.steps().get(0).target().origin().equals("USER"));
    }

    @Test
    public void walkedIdReplacesTheShell() throws Exception {
        MobileCaseIr ir = MobileAutomateIr.build(
                "TC_01", "com.app",
                List.of("Tap the \"Login\" button"),
                List.of(), List.of(),
                List.of(new MobileAutomateIr.Locator("id", "com.app:id/login", false)));
        String java = emit(ir);
        assertTrue(java.contains("AppiumBy.id(\"com.app:id/login\")"), java);
        assertTrue(ir.steps().get(0).target().origin().equals("HIERARCHY"));
        assertTrue("TC01".equals(ir.steps().get(0).screenKey()));
    }

    @Test
    public void aNewScreenBecomesItsOwnClass() throws Exception {
        MobileCaseIr ir = MobileAutomateIr.build(
                "TC_01", "com.app",
                List.of("Tap the \"Login\" button", "Tap the \"Pay\" button"),
                List.of(), List.of(),
                List.of(
                        new MobileAutomateIr.Locator("id", "com.app:id/login", false, "SignInab"),
                        new MobileAutomateIr.Locator("id", "com.app:id/pay", false, "Checkoutcd")));
        Path dir = Files.createTempDirectory("mobile-automate-screens");
        new MobileCodeWriter().writeVerified(dir, List.of(ir), NamingMap.empty());
        Path screens = dir.resolve("src/test/java/project/screens");
        assertTrue(Files.isRegularFile(screens.resolve("SignInabScreen.java")));
        assertTrue(Files.isRegularFile(screens.resolve("CheckoutcdScreen.java")));
    }

    @Test
    public void swipeUsesTheScrolledControl() throws Exception {
        MobileCaseIr ir = MobileAutomateIr.build(
                "TC_02", "com.app",
                List.of("Swipe up"),
                List.of(), List.of(),
                List.of(new MobileAutomateIr.Locator("id", "com.app:id/list", false)));
        String java = emit(ir, "TC02Screen.java");
        assertTrue(java.contains("AppiumBy.id(\"com.app:id/list\")"), java);
        assertTrue("HIERARCHY".equals(ir.steps().get(0).target().origin()));
    }

    private static String emit(MobileCaseIr ir) throws Exception {
        return emit(ir, "TC01Screen.java");
    }

    private static String emit(MobileCaseIr ir, String fileName) throws Exception {
        Path dir = Files.createTempDirectory("mobile-automate-ir");
        new MobileCodeWriter().writeVerified(dir, List.of(ir), NamingMap.empty());
        Path screen = dir.resolve("src/test/java/project/screens").resolve(fileName);
        assertTrue(Files.isRegularFile(screen), "screen written: " + fileName);
        return Files.readString(screen);
    }
}
