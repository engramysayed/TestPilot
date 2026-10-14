package delivery.mobile.apk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal ZIP "APK" fixtures for M1-02 tests (never real executables). */
public final class ApkFixtures {
    private ApkFixtures() {
    }

    public static Path writeZipApk(Path dir, String fileName, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(fileName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out;
    }

    public static Path validMinimalApk(Path dir, String name) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", "dummy-binary-manifest".getBytes(StandardCharsets.UTF_8));
        entries.put("classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a});
        return writeZipApk(dir, name, entries);
    }

    public static Path corruptNonZip(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(name);
        Files.writeString(out, "not-a-zip");
        return out;
    }

    static Path zipMissingManifest(Path dir, String name) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("classes.dex", new byte[]{1, 2, 3});
        return writeZipApk(dir, name, entries);
    }

    static Path zipWithTraversal(Path dir, String name) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("AndroidManifest.xml", "x".getBytes(StandardCharsets.UTF_8));
        entries.put("../evil.txt", "boom".getBytes(StandardCharsets.UTF_8));
        return writeZipApk(dir, name, entries);
    }

    /**
     * Build a ZIP that includes a Unix symlink entry (S_IFLNK in central-dir external attrs).
     * Written manually because some JDKs omit ZipEntry.setUnixMode / setExternalAttributes.
     */
    static Path zipWithSymlink(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve(name);
        List<RawEntry> entries = new ArrayList<>();
        entries.add(RawEntry.file("AndroidManifest.xml", "x".getBytes(StandardCharsets.UTF_8), 0100644));
        entries.add(RawEntry.file("link-out", "/tmp/escape".getBytes(StandardCharsets.UTF_8), 0120755));
        Files.write(out, buildUnixZip(entries));
        return out;
    }

    static byte[] bytesOfSize(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i & 0xff);
        }
        return b;
    }

    static byte[] zipBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private record RawEntry(String name, byte[] data, int unixMode) {
        static RawEntry file(String name, byte[] data, int unixMode) {
            return new RawEntry(name, data, unixMode);
        }
    }

    private static byte[] buildUnixZip(List<RawEntry> entries) {
        ByteArrayOutputStream local = new ByteArrayOutputStream();
        ByteArrayOutputStream central = new ByteArrayOutputStream();
        int offset = 0;
        for (RawEntry e : entries) {
            byte[] name = e.name().getBytes(StandardCharsets.UTF_8);
            byte[] data = e.data();
            CRC32 crc = new CRC32();
            crc.update(data);
            int crcVal = (int) crc.getValue();
            // local file header (store)
            ByteBuffer lh = ByteBuffer.allocate(30 + name.length).order(ByteOrder.LITTLE_ENDIAN);
            lh.putInt(0x04034b50);
            lh.putShort((short) 20);
            lh.putShort((short) 0);
            lh.putShort((short) 0); // store
            lh.putShort((short) 0);
            lh.putShort((short) 0);
            lh.putInt(crcVal);
            lh.putInt(data.length);
            lh.putInt(data.length);
            lh.putShort((short) name.length);
            lh.putShort((short) 0);
            lh.put(name);
            local.writeBytes(lh.array());
            local.writeBytes(data);
            // central directory header
            ByteBuffer ch = ByteBuffer.allocate(46 + name.length).order(ByteOrder.LITTLE_ENDIAN);
            ch.putInt(0x02014b50);
            ch.putShort((short) 0x0314); // Unix made-by
            ch.putShort((short) 20);
            ch.putShort((short) 0);
            ch.putShort((short) 0);
            ch.putShort((short) 0);
            ch.putShort((short) 0);
            ch.putInt(crcVal);
            ch.putInt(data.length);
            ch.putInt(data.length);
            ch.putShort((short) name.length);
            ch.putShort((short) 0);
            ch.putShort((short) 0);
            ch.putShort((short) 0);
            ch.putShort((short) 0);
            ch.putInt(e.unixMode() << 16);
            ch.putInt(offset);
            ch.put(name);
            central.writeBytes(ch.array());
            offset = local.size();
        }
        byte[] localBytes = local.toByteArray();
        byte[] cenBytes = central.toByteArray();
        ByteBuffer end = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        end.putInt(0x06054b50);
        end.putShort((short) 0);
        end.putShort((short) 0);
        end.putShort((short) entries.size());
        end.putShort((short) entries.size());
        end.putInt(cenBytes.length);
        end.putInt(localBytes.length);
        end.putShort((short) 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(localBytes);
        out.writeBytes(cenBytes);
        out.writeBytes(end.array());
        return out.toByteArray();
    }
}
