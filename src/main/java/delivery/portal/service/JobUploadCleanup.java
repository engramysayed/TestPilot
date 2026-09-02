package delivery.portal.service;

import java.nio.file.Files;
import java.nio.file.Path;

/** Deletes job-scoped uploads under {@code java.io.tmpdir/delivery-uploads}. */
public final class JobUploadCleanup {
    private JobUploadCleanup() {
    }

    public static void deleteIfTempUpload(Path path) {
        if (path == null) {
            return;
        }
        try {
            Path abs = path.toAbsolutePath().normalize();
            Path uploadsRoot = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads")
                    .toAbsolutePath().normalize();
            if (!abs.startsWith(uploadsRoot)) {
                return;
            }
            Files.deleteIfExists(abs);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }
}
