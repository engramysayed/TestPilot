package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class JobUploadCleanupTest {

    @Test
    public void deletesFileUnderDeliveryUploadsTemp() throws Exception {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", "prj_cleanup_test");
        Files.createDirectories(dir);
        Path excel = dir.resolve("job-upload.xlsx");
        Files.writeString(excel, "x");
        Assert.assertTrue(Files.isRegularFile(excel));

        JobUploadCleanup.deleteIfTempUpload(excel);

        Assert.assertFalse(Files.exists(excel));
    }

    @Test
    public void leavesFilesOutsideDeliveryUploadsAlone() throws Exception {
        Path outside = Files.createTempFile("not-delivery-upload-", ".xlsx");
        try {
            JobUploadCleanup.deleteIfTempUpload(outside);
            Assert.assertTrue(Files.isRegularFile(outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    public void nullPathIsNoOp() {
        JobUploadCleanup.deleteIfTempUpload(null);
    }
}
