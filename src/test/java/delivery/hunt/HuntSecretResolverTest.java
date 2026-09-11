package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HuntSecretResolverTest {

    @Test
    public void resolvesAllAliasForms() {
        HuntSecretResolver r = new HuntSecretResolver("alice", "s3cret", "245345");
        Assert.assertEquals(r.resolve("${TARGET_USERNAME}"), "alice");
        Assert.assertEquals(r.resolve("$TARGET_PASSWORD"), "s3cret");
        Assert.assertEquals(r.resolve("{{TARGET_OTP}}"), "245345");
        Assert.assertEquals(r.resolve("user=${TARGET_USERNAME}"), "user=alice");
    }

    @Test
    public void otpHintFromStoryNearOtpWord() {
        Assert.assertEquals(HuntSecretResolver.otpHintFromStory(
                "valid pass and login and static otp 245345"), "245345");
    }

    @Test
    public void containsTokenDetects() {
        Assert.assertTrue(HuntSecretResolver.containsToken("$TARGET_PASSWORD"));
        Assert.assertFalse(HuntSecretResolver.containsToken("invalid_user"));
    }
}
