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
    }

    private static String emit(MobileCaseIr ir) throws Exception {
        Path dir = Files.createTempDirectory("mobile-automate-ir");
        new MobileCodeWriter().writeVerified(dir, List.of(ir), NamingMap.empty());
        Path screen = dir.resolve("src/test/java/project/screens/MainScreen.java");
        assertTrue(Files.isRegularFile(screen), "screen written");
        return Files.readString(screen);
    }
}
