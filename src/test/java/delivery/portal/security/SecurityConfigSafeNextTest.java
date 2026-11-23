package delivery.portal.security;

import org.testng.Assert;
import org.testng.annotations.Test;

/** Login success redirect must only honor safe same-origin paths; everything else goes to /dashboard. */
public class SecurityConfigSafeNextTest {

    @Test
    public void nullOrBlank_fallsBackToDashboard() {
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard(null), "/dashboard");
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard(""), "/dashboard");
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("   "), "/dashboard");
    }

    @Test
    public void absoluteHttp_fallsBackToDashboard() {
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("https://evil.com/steal"), "/dashboard");
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("http://attacker.local/"), "/dashboard");
    }

    @Test
    public void schemeRelative_fallsBackToDashboard() {
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("//evil.com/path"), "/dashboard");
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("/\\evil.com"), "/dashboard");
    }

    @Test
    public void parentEscape_fallsBackToDashboard() {
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("/projects/../etc/passwd"), "/dashboard");
    }

    @Test
    public void loginLoop_fallsBackToDashboard() {
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("/login"), "/dashboard");
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("/login?error"), "/dashboard");
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("/logout"), "/dashboard");
    }

    @Test
    public void legitProjectPath_passesThrough() {
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("/projects/my-app"), "/projects/my-app");
        Assert.assertEquals(
                SecurityConfig.safeNextOrDashboard("/status?jobId=abc123"),
                "/status?jobId=abc123");
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("/automate"), "/automate");
    }

    @Test
    public void whitespaceIsTrimmed() {
        Assert.assertEquals(SecurityConfig.safeNextOrDashboard("  /projects/x  "), "/projects/x");
    }
}
