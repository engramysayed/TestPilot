package delivery.mobile.transport;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** M1-05: ZIP limits and secret-entry refusal. */
public class MobileInputZipValidatorTest {

    private static byte[] zip(String... namesAndContents) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zos.putNextEntry(new ZipEntry(namesAndContents[i]));
                zos.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    public void acceptsBoundedNonSecretZip() throws Exception {
        byte[] bytes = zip("job.json", "{}", "mobile-manifest.json", "{\"platform\":\"ANDROID\"}");
        MobileInputZipValidator.validate(bytes, 100, 10_000, 10_000);
    }

    @Test
    public void rejectsSecretEntryName() throws Exception {
        byte[] bytes = zip("secrets.json", "{\"password\":\"x\"}");
        try {
            MobileInputZipValidator.validate(bytes, 100, 10_000, 10_000);
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.ARTIFACT_INVALID);
        }
    }

    @Test
    public void rejectsPathTraversal() throws Exception {
        byte[] bytes = zip("../escape.txt", "x");
        try {
            MobileInputZipValidator.validate(bytes, 100, 10_000, 10_000);
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.ARTIFACT_INVALID);
        }
    }

    @Test
    public void rejectsTooManyEntries() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (int i = 0; i < 5; i++) {
                zos.putNextEntry(new ZipEntry("e" + i + ".txt"));
                zos.write("x".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        try {
            MobileInputZipValidator.validate(bos.toByteArray(), 3, 10_000, 10_000);
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.ZIP_LIMIT);
        }
    }

    @Test
    public void rejectsOversizedCompressed() throws Exception {
        byte[] bytes = zip("job.json", "x".repeat(1000));
        try {
            MobileInputZipValidator.validate(bytes, 100, 10_000, 50);
            Assert.fail();
        } catch (MobileTransportException e) {
            Assert.assertEquals(e.getCode(), MobileTransportErrorCode.ZIP_LIMIT);
        }
    }
}
