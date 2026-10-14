package delivery.job;

import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.store.DomainLocatorMemory;
import drivers.WebDriverFactory;
import org.openqa.selenium.chrome.*;
import org.testng.SkipException;
import org.testng.annotations.Test;
import java.nio.file.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class LocatorMemoryAssertionLiveSmokeTest {
    @Test public void recalledLocatorRunsRealVisibilityAssertions() throws Exception {
        String executable = System.getProperty("keel.performance.chromedriver", "");
        if (executable.isBlank()) throw new SkipException("Explicit existing ChromeDriver required");
        Path page = Files.createTempFile("memory-assertion-", ".html");
        Path evidence = Files.createTempDirectory("memory-assertion-evidence-");
        ChromeDriver driver = null;
        try {
            Files.writeString(page, "<html><body><input id='otp' aria-label='OTP'></body></html>");
            driver = new ChromeDriver(new ChromeDriverService.Builder().usingDriverExecutable(Path.of(executable).toFile()).build(),
                    new ChromeOptions().addArguments("--headless=new", "--window-size=1000,700"));
            driver.get(page.toUri().toString());
            var memory = new DomainLocatorMemory();
            memory.remember("fixture", "/login", new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_FIELD,
                    "Enter in the OTP field"), new ProvenStep("TC", "Page", "elementAction", "type", "id", "otp", "", "", "", true, ""));
            var factory = mock(WebDriverFactory.class);
            when(factory.get()).thenReturn(driver);
            var execution = new TcExecutionService(factory);
            var visible = memory.recall("fixture", "/login", new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                    "Confirm the OTP field is visible"), "TC").orElseThrow();
            var absent = memory.recall("fixture", "/login", new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                    "Confirm the OTP field is not visible"), "TC").orElseThrow();
            assertEquals(execution.execute("TC", List.of(visible), evidence).status(), TcStatus.PASSED);
            assertNotEquals(execution.execute("TC", List.of(absent), evidence).status(), TcStatus.PASSED);
            driver.executeScript("document.getElementById('otp').style.display='none'");
            assertEquals(execution.execute("TC", List.of(absent), evidence).status(), TcStatus.PASSED);
        } finally {
            try { if (driver != null) driver.quit(); } finally { Files.deleteIfExists(page); }
        }
    }
}
