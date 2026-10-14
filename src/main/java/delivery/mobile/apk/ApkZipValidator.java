package delivery.mobile.apk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Content validation for uploaded APK archives: ZIP structure, bounded size/entries,
 * path traversal, unsafe names, and symlink escapes. Never executes archive content.
 * <p>Symlink detection reads central-directory external attributes directly so it works
 * on JDKs whose {@link ZipEntry} omits Unix-mode helpers.
 */
public final class ApkZipValidator {
    private static final String MANIFEST = "AndroidManifest.xml";
    /** Unix S_IFLNK */
    private static final int S_IFLNK = 0120000;
    private static final int CEN_SIG = 0x02014b50;
    private static final int END_SIG = 0x06054b50;

    private ApkZipValidator() {
    }

    public static void validate(Path apk, int maxEntries, long maxUncompressedBytes) throws ApkInspectionException {
        if (apk == null || !Files.isRegularFile(apk)) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "APK file missing");
        }
        try {
            long size = Files.size(apk);
            if (size <= 0) {
                throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "empty APK");
            }
        } catch (IOException e) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "cannot read APK size");
        }

        Set<String> symlinkNames = readSymlinkEntryNames(apk);

        boolean sawManifest = false;
        int entries = 0;
        long uncompressed = 0L;
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                entries++;
                if (entries > maxEntries) {
                    throw new ApkInspectionException(ApkErrorCode.OVERSIZED,
                            "ZIP entry count exceeds limit " + maxEntries);
                }
                String name = entry.getName();
                rejectUnsafeName(name);
                if (symlinkNames.contains(name)) {
                    throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME,
                            "symlink entry refused: " + name);
                }
                long u = entry.getSize();
                if (u < 0) {
                    u = 0;
                }
                uncompressed += u;
                if (uncompressed > maxUncompressedBytes) {
                    throw new ApkInspectionException(ApkErrorCode.OVERSIZED,
                            "uncompressed ZIP payload exceeds limit");
                }
                if (MANIFEST.equals(name) || name.endsWith("/" + MANIFEST)) {
                    sawManifest = true;
                }
            }
        } catch (ApkInspectionException e) {
            throw e;
        } catch (IOException e) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID,
                    "corrupt or non-ZIP APK: " + e.getMessage());
        }
        if (!sawManifest) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "AndroidManifest.xml missing");
        }
    }

    static void rejectUnsafeName(String name) throws ApkInspectionException {
        if (name == null || name.isEmpty()) {
            throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME, "empty ZIP entry name");
        }
        if (name.indexOf('\0') >= 0) {
            throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME, "null byte in entry name");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//")) {
            throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME, "absolute ZIP path refused");
        }
        String[] parts = normalized.split("/");
        for (String p : parts) {
            if ("..".equals(p)) {
                throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME,
                        "path traversal refused: " + name);
            }
        }
        if (normalized.contains(":")) {
            throw new ApkInspectionException(ApkErrorCode.UNSAFE_NAME, "unsafe ZIP entry name: " + name);
        }
    }

    /**
     * Parse ZIP central directory for entries whose Unix mode has S_IFLNK.
     * Best-effort: IO errors yield an empty set (ZipFile open will still catch corrupt archives).
     */
    static Set<String> readSymlinkEntryNames(Path apk) {
        Set<String> symlinks = new HashSet<>();
        try (FileChannel ch = FileChannel.open(apk, StandardOpenOption.READ)) {
            long size = ch.size();
            if (size < 22) {
                return symlinks;
            }
            int scan = (int) Math.min(size, 64 * 1024);
            ByteBuffer tail = ByteBuffer.allocate(scan).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(tail, size - scan);
            tail.flip();
            int endPos = -1;
            for (int i = tail.limit() - 22; i >= 0; i--) {
                if (tail.getInt(i) == END_SIG) {
                    endPos = i;
                    break;
                }
            }
            if (endPos < 0) {
                return symlinks;
            }
            long cenOffset = Integer.toUnsignedLong(tail.getInt(endPos + 16));
            int cenSize = tail.getInt(endPos + 12);
            if (cenOffset < 0 || cenSize <= 0 || cenOffset + cenSize > size) {
                return symlinks;
            }
            ByteBuffer cen = ByteBuffer.allocate(cenSize).order(ByteOrder.LITTLE_ENDIAN);
            ch.read(cen, cenOffset);
            cen.flip();
            while (cen.remaining() >= 46) {
                int sig = cen.getInt();
                if (sig != CEN_SIG) {
                    break;
                }
                cen.position(cen.position() + 4); // ver made/needed
                cen.getShort(); // flags
                cen.getShort(); // method
                cen.getInt(); // time/date
                cen.getInt(); // crc
                cen.getInt(); // comp size
                cen.getInt(); // uncomp size
                int nameLen = Short.toUnsignedInt(cen.getShort());
                int extraLen = Short.toUnsignedInt(cen.getShort());
                int commentLen = Short.toUnsignedInt(cen.getShort());
                cen.getShort(); // disk
                cen.getShort(); // internal attrs
                int externalAttrs = cen.getInt();
                cen.getInt(); // local header offset
                if (cen.remaining() < nameLen) {
                    break;
                }
                byte[] nameBytes = new byte[nameLen];
                cen.get(nameBytes);
                String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
                int mode = (externalAttrs >>> 16) & 0xFFFF;
                if ((mode & S_IFLNK) == S_IFLNK) {
                    symlinks.add(name);
                }
                if (cen.remaining() < extraLen + commentLen) {
                    break;
                }
                cen.position(cen.position() + extraLen + commentLen);
            }
        } catch (IOException ignored) {
            return symlinks;
        }
        return symlinks;
    }
}
