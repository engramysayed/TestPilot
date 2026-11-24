package delivery.mobile.automate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The phone picked for an Automate job, kept on disk so a portal restart still uses it. */
public final class MobileDeviceChoice {

    private MobileDeviceChoice() {
    }

    public static void save(Path storeRoot, String jobId, String deviceId) {
        Path file = file(storeRoot, jobId);
        if (file == null) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, deviceId == null ? "" : deviceId, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // The in-memory choice still works for this process.
        }
    }

    /** Null when this job has no saved choice. Blank means the user chose no phone. */
    public static String load(Path storeRoot, String jobId) {
        Path file = file(storeRoot, jobId);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static Path file(Path storeRoot, String jobId) {
        if (storeRoot == null || jobId == null || jobId.isBlank()) {
            return null;
        }
        String safe = jobId.trim();
        if (!safe.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,80}")) {
            return null;
        }
        return storeRoot.resolve("mobile-device-choices").resolve(safe);
    }
}
