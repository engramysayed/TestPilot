package delivery.agent.harness.convert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/** ZIP with Unix symlink external attributes (manual central-dir; JDK-safe). */
final class SymlinkZipFixtures {
    private SymlinkZipFixtures() {
    }

    static Path writeZipWithSymlinkEntry(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path out = dir.resolve("symlink.zip");
        List<RawEntry> entries = new ArrayList<>();
        entries.add(RawEntry.file("src/ok.txt", "ok".getBytes(StandardCharsets.UTF_8), 0100644));
        entries.add(RawEntry.file("link-out", "../outside".getBytes(StandardCharsets.UTF_8), 0120755));
        Files.write(out, buildUnixZip(entries));
        return out;
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
            ByteBuffer lh = ByteBuffer.allocate(30 + name.length).order(ByteOrder.LITTLE_ENDIAN);
            lh.putInt(0x04034b50);
            lh.putShort((short) 20);
            lh.putShort((short) 0);
            lh.putShort((short) 0);
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
            ByteBuffer ch = ByteBuffer.allocate(46 + name.length).order(ByteOrder.LITTLE_ENDIAN);
            ch.putInt(0x02014b50);
            ch.putShort((short) 0x0314);
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
