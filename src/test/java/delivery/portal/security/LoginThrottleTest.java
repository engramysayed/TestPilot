package delivery.portal.security;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;

public class LoginThrottleTest {

    @Test
    public void fifthFailureLocksThePrincipal() {
        LoginThrottle throttle = new LoginThrottle(5, 15 * 60);
        Instant t0 = Instant.parse("2026-09-17T00:00:00Z");
        String key = "admin@localhost|127.0.0.1";
        for (int i = 0; i < 5; i++) {
            Assert.assertTrue(throttle.allow(key, t0.plusSeconds(i)));
            throttle.recordFailure(key, t0.plusSeconds(i));
        }
        Assert.assertFalse(throttle.allow(key, t0.plusSeconds(10)));
        Assert.assertTrue(throttle.retryAfterSeconds(key, t0.plusSeconds(10)) > 0);
        throttle.recordSuccess(key);
        Assert.assertTrue(throttle.allow(key, t0.plusSeconds(11)));
    }

    @Test
    public void lockExpiresAfterWindow() {
        LoginThrottle throttle = new LoginThrottle(3, 60);
        Instant t0 = Instant.parse("2026-09-17T00:00:00Z");
        String key = "user@localhost|10.0.0.2";
        throttle.recordFailure(key, t0);
        throttle.recordFailure(key, t0.plusSeconds(1));
        throttle.recordFailure(key, t0.plusSeconds(2));
        Assert.assertFalse(throttle.allow(key, t0.plusSeconds(3)));
        Assert.assertTrue(throttle.allow(key, t0.plusSeconds(61)));
    }
}
