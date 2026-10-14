package delivery.job;

import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.testng.Assert.*;

/** B21: JobLoginService throws a structured LOGIN_FAILED (code + reason) with the same message text as before. */
public class LoginFailedCodeTest {
    private static WebDriver driverThrowing(RuntimeException e) {
        return (WebDriver) Proxy.newProxyInstance(WebDriver.class.getClassLoader(), new Class<?>[]{WebDriver.class},
                (proxy, m, args) -> {
                    if (m.getName().equals("getCurrentUrl")) return "https://app.test/login";
                    if (m.getName().equals("toString")) return "fake-driver";
                    throw e;
                });
    }

    private static LoginFailedException login(RuntimeException fromDriver) {
        return expectThrows(LoginFailedException.class,
                () -> new JobLoginService().login(driverThrowing(fromDriver), "qa", "pw", List.of()));
    }

    @Test
    public void missingPasswordIsStructured() {
        ConversionJobRequestStub req = new ConversionJobRequestStub();
        LoginFailedException e = expectThrows(LoginFailedException.class,
                () -> new JobLoginService().loginIfNeeded(null, req.withUserNoPassword()));
        assertEquals(e.code(), "LOGIN_FAILED");
        assertEquals(e.reason(), LoginFailedException.Reason.MISSING_PASSWORD);
        assertEquals(e.getMessage(), "LOGIN_FAILED: password is required when username is provided");
    }

    @Test
    public void timeoutAndDriverErrorsAreStructuredWithLegacyMessage() {
        LoginFailedException t = login(new TimeoutException("no login form"));
        assertEquals(t.reason(), LoginFailedException.Reason.TIMEOUT);
        assertTrue(t.getMessage().startsWith("LOGIN_FAILED: no login form"), t.getMessage());
        LoginFailedException d = login(new WebDriverException("chrome crashed"));
        assertEquals(d.reason(), LoginFailedException.Reason.DRIVER_ERROR);
        assertTrue(d.getMessage().startsWith("LOGIN_FAILED: chrome crashed"), d.getMessage());
        assertTrue(d instanceof IllegalStateException, "message-based callers keep working");
    }

    @Test
    public void findWalksTheCauseChain() {
        LoginFailedException e = new LoginFailedException(LoginFailedException.Reason.STILL_ON_LOGIN_FORM, "x", null);
        assertSame(LoginFailedException.find(new RuntimeException("wrap", new IllegalStateException("w", e))), e);
        assertNull(LoginFailedException.find(new RuntimeException("other")));
        assertNull(LoginFailedException.find(null));
    }

    /** Builds a ConversionJobRequest with only username set, via the canonical constructor. */
    static final class ConversionJobRequestStub {
        ConversionJobRequest withUserNoPassword() throws Exception {
            var ctor = ConversionJobRequest.class.getDeclaredConstructors()[0];
            for (var c : ConversionJobRequest.class.getDeclaredConstructors()) {
                if (c.getParameterCount() > ctor.getParameterCount()) ctor = c;
            }
            Object[] args = new Object[ctor.getParameterCount()];
            var comps = ConversionJobRequest.class.getRecordComponents();
            var types = ctor.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                if (types[i] == boolean.class) args[i] = false;
                else if (types[i] == int.class) args[i] = 0;
                else if (types[i] == long.class) args[i] = 0L;
                if (i < comps.length && comps[i].getName().equals("username")) args[i] = "qa";
            }
            return (ConversionJobRequest) ctor.newInstance(args);
        }
    }
}
