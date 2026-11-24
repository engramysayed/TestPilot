package delivery.mobile.adb;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns Android {@code screenrecord} annex-B into fragmented MP4 a browser can play.
 * Width and height are the size we asked screenrecord to emit.
 */
public final class H264Fmp4 {

    private final int width;
    private final int height;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private byte[] sps;
    private byte[] pps;
    private boolean initSent;
    private int sequence;
    private long decodeTime;

    public H264Fmp4(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public byte[] push(byte[] chunk) {
        if (chunk != null && chunk.length > 0) {
            pending.writeBytes(chunk);
        }
        byte[] raw = pending.toByteArray();
        List<byte[]> nals = new ArrayList<>();
        int consumed = takeNals(raw, nals);
        if (consumed > 0) {
            pending.reset();
            if (consumed < raw.length) {
                pending.write(raw, consumed, raw.length - consumed);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] nal : nals) {
            int type = nal.length == 0 ? 0 : (nal[0] & 0x1F);
            if (type == 7) {
                sps = nal;
            } else if (type == 8) {
                pps = nal;
            } else if (type == 1 || type == 5) {
                if (sps == null || pps == null) {
                    continue;
                }
                if (!initSent) {
                    out.writeBytes(init());
                    initSent = true;
                }
                out.writeBytes(fragment(nal));
            }
        }
        return out.toByteArray();
    }

    private byte[] init() {
        return concat(box("ftyp", concat(ascii("isom"), u32(0), ascii("isom"), ascii("iso6"), ascii("avc1"))),
                box("moov", concat(mvhd(), trak(), mvex())));
    }

    private byte[] fragment(byte[] nal) {
        byte[] sample = concat(u32(nal.length), nal);
        sequence++;
        byte[] moof = moof(0, sample.length);
        moof = moof(moof.length + 8, sample.length);
        decodeTime += 3000;
        return concat(moof, box("mdat", sample));
    }

    private byte[] moof(int dataOffset, int sampleLength) {
        return box("moof", concat(
                full("mfhd", 0, u32(sequence)),
                box("traf", concat(
                        full("tfhd", 0, u32(1)),
                        full("tfdt", 0, u32((int) decodeTime)),
                        full("trun", 0x000001 | 0x000200 | 0x000400,
                                concat(u32(1), u32(dataOffset), u32(3000), u32(sampleLength)))
                ))));
    }

    private byte[] mvhd() {
        return full("mvhd", 0, concat(
                u32(0), u32(0), u32(30000), u32(0),
                u32(0x00010000), u16(0x0100), new byte[10],
                u32(0x00010000), u32(0), u32(0), u32(0), u32(0x00010000), u32(0),
                u32(0), u32(0), u32(0x40000000),
                new byte[24], u32(2)));
    }

    private byte[] trak() {
        return box("trak", concat(tkhd(), mdia()));
    }

    private byte[] tkhd() {
        return full("tkhd", 7, concat(
                u32(0), u32(0), u32(1), u32(0), u32(0),
                new byte[8], u16(0), u16(0), u16(0), u16(0),
                u32(0x00010000), u32(0), u32(0), u32(0), u32(0x00010000), u32(0),
                u32(0), u32(0), u32(0x40000000),
                u32(width << 16), u32(height << 16)));
    }

    private byte[] mdia() {
        return box("mdia", concat(
                full("mdhd", 0, concat(u32(0), u32(0), u32(30000), u32(0), u16(0x55c4), u16(0))),
                full("hdlr", 0, concat(u32(0), ascii("vide"), new byte[12], ascii("VideoHandler\0"))),
                box("minf", concat(
                        full("vmhd", 1, concat(u16(0), u16(0), u16(0), u16(0))),
                        box("dinf", full("dref", 0, concat(u32(1), full("url ", 1, new byte[0])))),
                        box("stbl", concat(
                                box("stsd", concat(u32(0), u32(1), avc1())),
                                full("stts", 0, u32(0)),
                                full("stsc", 0, u32(0)),
                                full("stsz", 0, concat(u32(0), u32(0))),
                                full("stco", 0, u32(0))
                        ))
                ))
        ));
    }

    private byte[] avc1() {
        return sampleEntry(avcC());
    }

    private byte[] sampleEntry(byte[] avcC) {
        byte[] visual = new byte[86];
        ByteBuffer.wrap(visual).putInt(86 + avcC.length).put(ascii("avc1"));
        put16(visual, 14, 1);
        put16(visual, 32, width);
        put16(visual, 34, height);
        put32(visual, 36, 0x00480000);
        put32(visual, 40, 0x00480000);
        put16(visual, 48, 1);
        put16(visual, 82, 0x0018);
        put16(visual, 84, 0xFFFF);
        return concat(visual, avcC);
    }

    private byte[] avcC() {
        byte profile = sps.length > 1 ? sps[1] : 0x42;
        byte compat = sps.length > 2 ? sps[2] : 0;
        byte level = sps.length > 3 ? sps[3] : 0x1f;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new byte[] {1, profile, compat, level, (byte) 0xFF, (byte) 0xE1});
        o.writeBytes(u16(sps.length));
        o.writeBytes(sps);
        o.write(1);
        o.writeBytes(u16(pps.length));
        o.writeBytes(pps);
        return box("avcC", o.toByteArray());
    }

    private byte[] mvex() {
        return box("mvex", full("trex", 0, concat(u32(1), u32(1), u32(0), u32(0), u32(0))));
    }

    private static int takeNals(byte[] raw, List<byte[]> nals) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i + 3 < raw.length; i++) {
            if (raw[i] == 0 && raw[i + 1] == 0 && raw[i + 2] == 1) {
                starts.add(i);
                i += 2;
            }
        }
        if (starts.size() < 2) {
            return 0;
        }
        int last = starts.get(starts.size() - 1);
        for (int s = 0; s < starts.size() - 1; s++) {
            int from = starts.get(s);
            int to = starts.get(s + 1);
            int nalStart = from + (raw[from + 2] == 1 ? 3 : 4);
            if (from + 3 < raw.length && raw[from + 2] == 0) {
                nalStart = from + 4;
            }
            if (nalStart < to) {
                byte[] nal = new byte[to - nalStart];
                System.arraycopy(raw, nalStart, nal, 0, nal.length);
                nals.add(nal);
            }
        }
        return last;
    }

    private static byte[] box(String type, byte[] payload) {
        return concat(u32(8 + payload.length), ascii(type), payload);
    }

    private static byte[] full(String type, int flags, byte[] payload) {
        return box(type, concat(new byte[] {0, (byte) ((flags >> 16) & 0xFF), (byte) ((flags >> 8) & 0xFF), (byte) (flags & 0xFF)}, payload));
    }

    private static byte[] u32(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    private static byte[] u16(int v) {
        return ByteBuffer.allocate(2).putShort((short) v).array();
    }

    private static void put16(byte[] a, int at, int v) {
        a[at] = (byte) ((v >> 8) & 0xFF);
        a[at + 1] = (byte) (v & 0xFF);
    }

    private static void put32(byte[] a, int at, int v) {
        ByteBuffer.wrap(a).position(at);
        a[at] = (byte) ((v >> 24) & 0xFF);
        a[at + 1] = (byte) ((v >> 16) & 0xFF);
        a[at + 2] = (byte) ((v >> 8) & 0xFF);
        a[at + 3] = (byte) (v & 0xFF);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }
}
