package delivery.mobile.transport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates frozen runner input ZIP archives (non-secret). Enforces entry/size/path rules.
 * Secrets must travel via {@link MobileSecretChannel}, never as ZIP entries.
 */
public final class MobileInputZipValidator {
    private static final Set<String> FORBIDDEN_ENTRY_NAMES = Set.of(
            "secrets.json", "secrets.enc", "credentials.json", "password.txt", "secret-envelope.json"
    );

    private MobileInputZipValidator() {
    }

    public static void validate(byte[] zipBytes, int maxEntries, long maxUncompressedBytes, long maxCompressedBytes) {
        if (zipBytes == null) {
            throw new MobileTransportException(MobileTransportErrorCode.ZIP_LIMIT, "ZIP body missing");
        }
        if (zipBytes.length > maxCompressedBytes) {
            throw new MobileTransportException(MobileTransportErrorCode.ZIP_LIMIT,
                    "compressed ZIP exceeds limit " + maxCompressedBytes);
        }
        int entries = 0;
        long uncompressed = 0L;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries++;
                if (entries > maxEntries) {
                    throw new MobileTransportException(MobileTransportErrorCode.ZIP_LIMIT,
                            "ZIP entry count exceeds limit " + maxEntries);
                }
                String name = entry.getName();
                rejectUnsafeName(name);
                String base = baseName(name).toLowerCase(Locale.ROOT);
                if (FORBIDDEN_ENTRY_NAMES.contains(base)) {
                    throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID,
                            "secret entry refused in input ZIP: " + name);
                }
                long size = entry.getSize();
                if (size < 0) {
                    // Unknown size: drain with bound.
                    size = drainBounded(zis, maxUncompressedBytes - uncompressed + 1);
                } else {
                    drainBounded(zis, size + 1);
                }
                uncompressed += size;
                if (uncompressed > maxUncompressedBytes) {
                    throw new MobileTransportException(MobileTransportErrorCode.ZIP_LIMIT,
                            "uncompressed ZIP payload exceeds limit");
                }
                zis.closeEntry();
            }
        } catch (MobileTransportException e) {
            throw e;
        } catch (IOException e) {
            throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID,
                    "corrupt or non-ZIP input: " + e.getMessage());
        }
        if (entries == 0) {
            throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID, "empty input ZIP");
        }
    }

    static void rejectUnsafeName(String name) {
        if (name == null || name.isEmpty()) {
            throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID, "empty ZIP entry name");
        }
        if (name.indexOf('\0') >= 0) {
            throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID, "null byte in entry name");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//")) {
            throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID, "absolute ZIP path refused");
        }
        for (String p : normalized.split("/")) {
            if ("..".equals(p)) {
                throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID,
                        "path traversal refused: " + name);
            }
        }
    }

    private static String baseName(String name) {
        String n = name.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        return slash < 0 ? n : n.substring(slash + 1);
    }

    private static long drainBounded(ZipInputStream zis, long maxRead) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = zis.read(buf)) >= 0) {
            total += n;
            if (total > maxRead) {
                throw new MobileTransportException(MobileTransportErrorCode.ZIP_LIMIT,
                        "uncompressed ZIP payload exceeds limit");
            }
        }
        return total;
    }
}
