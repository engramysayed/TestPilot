package delivery.portal.record;

import org.testng.Assert;
import org.testng.annotations.Test;

public class RecordingUrlsTest {

    @Test
    public void projectWebsiteIsTheStartAddress() {
        Assert.assertEquals(
                RecordingUrls.resolve("https://shop.example/catalog", "", true),
                "https://shop.example/catalog");
    }

    @Test
    public void localhostProjectCanBeRecorded() {
        Assert.assertEquals(
                RecordingUrls.resolve("http://127.0.0.1:8080", "", true),
                "http://127.0.0.1:8080");
    }

    @Test
    public void missingProjectWebsiteIsExplained() {
        try {
            RecordingUrls.resolve("", "https://shop.example", true);
            Assert.fail("expected a message");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "Set the project website in Settings first.");
        }
    }

    @Test
    public void otherHostIsRefused() {
        try {
            RecordingUrls.resolve("https://shop.example", "https://evil.test/cart", false);
            Assert.fail("expected a refusal");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "That address has to be this project's website.");
        }
    }

    @Test
    public void privateAddressIsRefused() {
        try {
            RecordingUrls.resolve("https://shop.example", "http://127.0.0.1:9", false);
            Assert.fail("expected a refusal");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("private network"), e.getMessage());
        }
    }

    @Test
    public void addressMustIncludeAScheme() {
        try {
            RecordingUrls.resolve("https://shop.example", "shop.example/cart", false);
            Assert.fail("expected a refusal");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "Type a full http or https address.");
        }
    }

    @Test
    public void userInfoIsStripped() {
        Assert.assertEquals(
                RecordingUrls.safeUrl("https://user:s3cret@shop.example/login"),
                "https://shop.example/login");
    }
}
