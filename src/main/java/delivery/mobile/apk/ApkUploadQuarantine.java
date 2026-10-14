package delivery.mobile.apk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Streams APK uploads into a tenant/project quarantine directory with size limit and SHA-256.
 * Refuses remote APK URLs and path-traversal / unsafe original filenames.
 */
public final class ApkUploadQuarantine {
    public record QuarantinedUpload(Path apkPath, String uploadId, String sha256Hex, long byteCount,
                                    String safeOriginalName) {
    }

    private ApkUploadQuarantine() {
    }

    public static Path quarantineRoot(Path storeRoot, String tenantId, String projectId) {
        return storeRoot
                .resolve("tenants").resolve(safeSegment(tenantId))
                .resolve("projects").resolve(safeSegment(projectId))
                .resolve("mobile").resolve("quarantine");
    }

    public static Path revisionApkPath(Path storeRoot, String tenantId, String projectId, String appRevisionId) {
        return storeRoot
                .resolve("tenants").resolve(safeSegment(tenantId))
                .resolve("projects").resolve(safeSegment(projectId))
                .resolve("mobile").resolve("apps").resolve(safeSegment(appRevisionId))
                .resolve("app.apk");
    }

    public static QuarantinedUpload streamToQuarantine(Path storeRoot,
                                                       String tenantId,
                                                       String projectId,
                                                       String originalFilename,
                                                       InputStream in,
                                                       long maxBytes) throws ApkInspectionException, IOException {
        if (in == null) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "upload stream required");
        }
        rejectRemoteUrl(originalFilename);
        String safeName = sanitizeFilename(originalFilename);
        String uploadId = "upl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Path dir = quarantineRoot(storeRoot, tenantId, projectId).resolve(uploadId);
        Files.createDirectories(dir);
        Path dest = dir.resolve("upload.apk");

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 unavailable", e);
        }

        long counted = 0L;
        byte[] buf = new byte[8192];
        try (DigestInputStream din = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(dest)) {
            int n;
            while ((n = din.read(buf)) >= 0) {
                counted += n;
                if (counted > maxBytes) {
                    out.close();
                    deleteQuietly(dir);
                    throw new ApkInspectionException(ApkErrorCode.OVERSIZED,
                            "upload exceeds limit of " + maxBytes + " bytes");
                }
                out.write(buf, 0, n);
            }
        } catch (ApkInspectionException e) {
            deleteQuietly(dir);
            throw e;
        }
        if (counted == 0L) {
            deleteQuietly(dir);
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "empty upload");
        }
        String sha = HexFormat.of().formatHex(digest.digest());
        return new QuarantinedUpload(dest, uploadId, sha, counted, safeName);
    }

    public static void promoteToRevision(QuarantinedUpload upload, Path revisionApk) throws IOException {
        Files.createDirectories(revisionApk.getParent());
        Files.move(upload.apkPath(), revisionApk);
        // remove empty quarantine upload dir when possible
        Path parent = upload.apkPath().getParent();
        if (parent != null) {
            try {
                Files.deleteIfExists(parent);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    public static void discard(QuarantinedUpload upload) {
        if (upload == null || upload.apkPath() == null) {
            return;
        }
        Path parent = upload.apkPath().getParent();
        deleteQuietly(parent != null ? parent : upload.apkPath());
    }

    static void rejectRemoteUrl(String originalFilename) throws ApkInspectionException {
        if (originalFilename == null) {
            return;
        }
        String t = originalFilename.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://")
                || t.startsWith("ftp://") || t.contains("://")) {
            throw new ApkInspectionException(ApkErrorCode.REMOTE_SOURCE_REJECTED,
                    "remote APK URLs are refused");
        }
    }

    static String sanitizeFilename(String originalFilename) throws ApkInspectionException {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.apk";
        }
        if (originalFilename.contains("..") || originalFilename.indexOf('\0') >= 0) {
            throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME,
                    "unsafe upload filename");
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank() || name.contains("..") || name.indexOf('\0') >= 0) {
            throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME,
                    "unsafe upload filename");
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            // still allow quarantine of misnamed files; inspector will reject content
            return name;
        }
        return name;
    }

    static String safeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("path segment required");
        }
        String s = raw.trim();
        if (s.contains("..") || s.contains("/") || s.contains("\\") || s.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("unsafe path segment");
        }
        return s;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(ApkUploadQuarantine::deleteQuietly);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
