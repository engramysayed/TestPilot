package delivery.portal.security;

import org.testng.Assert;
import org.testng.annotations.Test;

public class JobSecretCryptoTest {
    @Test
    public void roundTripEncryptDecrypt() {
        System.setProperty("DELIVERY_SECRET_KEY", "unit-test-secret-key-please");
        String plain = "Password123!";
        String cipher = JobSecretCrypto.encrypt(plain);
        Assert.assertNotEquals(cipher, plain);
        Assert.assertFalse(cipher.contains(plain));
        Assert.assertEquals(JobSecretCrypto.decrypt(cipher), plain);
    }
}
