package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DomPostClickValidatorTest {

    @Test
    public void urlChangeIsOk() {
        DomPostClickValidator.Snapshot before =
                new DomPostClickValidator.Snapshot("https://a/login", "Login", "abc:10", true);
        DomPostClickValidator.Snapshot after =
                new DomPostClickValidator.Snapshot("https://a/secure", "Secure", "abc:10", true);
        DomPostClickValidator.Result r = DomPostClickValidator.compare(before, after, "Login");
        Assert.assertEquals(r.status(), DomPostClickValidator.Status.OK);
        Assert.assertEquals(r.reason(), "url-changed");
    }

    @Test
    public void loginFormClearedIsOk() {
        DomPostClickValidator.Snapshot before =
                new DomPostClickValidator.Snapshot("https://a/login", "Login", "abc:10", true);
        DomPostClickValidator.Snapshot after =
                new DomPostClickValidator.Snapshot("https://a/login", "Login", "abc:10", false);
        DomPostClickValidator.Result r = DomPostClickValidator.compare(before, after, "Login");
        Assert.assertEquals(r.status(), DomPostClickValidator.Status.OK);
        Assert.assertEquals(r.reason(), "login-form-cleared");
    }

    @Test
    public void bodyOnlyIsWeak() {
        DomPostClickValidator.Snapshot before =
                new DomPostClickValidator.Snapshot("https://a/x", "T", "aaa:1", false);
        DomPostClickValidator.Snapshot after =
                new DomPostClickValidator.Snapshot("https://a/x", "T", "bbb:2", false);
        DomPostClickValidator.Result r = DomPostClickValidator.compare(before, after, "x");
        Assert.assertEquals(r.status(), DomPostClickValidator.Status.WEAK);
    }

    @Test
    public void noChangeIsFail() {
        DomPostClickValidator.Snapshot s =
                new DomPostClickValidator.Snapshot("https://a/x", "T", "aaa:1", false);
        DomPostClickValidator.Result r = DomPostClickValidator.compare(s, s, "x");
        Assert.assertEquals(r.status(), DomPostClickValidator.Status.FAIL);
        Assert.assertTrue(r.failsStrict());
    }

    @Test
    public void skipWhenNotClick() {
        DomPostClickValidator.Result r = DomPostClickValidator.validate(
                new DomPostClickValidator.Snapshot("u", "t", "b", false),
                null,
                "type",
                "user");
        Assert.assertEquals(r.status(), DomPostClickValidator.Status.SKIP);
    }
}
