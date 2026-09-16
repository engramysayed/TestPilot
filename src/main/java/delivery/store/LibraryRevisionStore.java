package delivery.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable library revisions. Writes require the caller's base revision; stale bases conflict.
 */
public final class LibraryRevisionStore {
    public record Revision(String id, String parentId, String source, String author, Instant createdAt, String sha256) {
    }

    private static final String HEAD = "HEAD";

    private final Path root;

    public LibraryRevisionStore(Path root) {
        this.root = root;
    }

    public synchronized Optional<Revision> head() throws IOException {
        Path head = root.resolve(HEAD);
        if (!Files.isRegularFile(head)) {
            return Optional.empty();
        }
        return Optional.of(readMeta(Files.readString(head, StandardCharsets.UTF_8).trim()));
    }

    public synchronized List<Revision> list() throws IOException {
        Path dir = revisionsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Revision> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                out.add(parseMeta(Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        out.sort(Comparator.comparing(Revision::createdAt));
        return List.copyOf(out);
    }

    public synchronized Revision commit(String expectedBaseId, String source, String author, byte[] payload)
            throws IOException {
        Optional<Revision> current = head();
        String headId = current.map(Revision::id).orElse(null);
        String expected = expectedBaseId == null || expectedBaseId.isBlank() ? headId : expectedBaseId;
        if (headId != null && (expected == null || !headId.equals(expected))) {
            throw new StaleLibraryRevisionException(expected == null ? "" : expected, headId);
        }
        if (headId == null && expected != null && !expected.isBlank()) {
            throw new StaleLibraryRevisionException(expected, "");
        }
        String id = "rev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        byte[] body = payload == null ? new byte[0] : payload;
        Files.createDirectories(revisionsDir());
        Files.write(revisionsDir().resolve(id + ".bin"), body);
        Revision rev = new Revision(id, headId == null ? "" : headId,
                source == null ? "" : source,
                author == null ? "" : author,
                Instant.now(),
                sha256(body));
        Files.writeString(revisionsDir().resolve(id + ".json"), toJson(rev), StandardCharsets.UTF_8);
        Files.writeString(root.resolve(HEAD), id, StandardCharsets.UTF_8);
        Files.write(root.resolve("latest.bin"), body);
        return rev;
    }

    public synchronized Revision restoreAsNew(String revisionId, String author) throws IOException {
        Revision source = readMeta(revisionId);
        byte[] body = Files.readAllBytes(revisionsDir().resolve(source.id() + ".bin"));
        String parent = head().map(Revision::id).orElse("");
        return commit(parent, "restore:" + source.id(), author, body);
    }

    public Path bytes(String revisionId) {
        return revisionsDir().resolve(revisionId + ".bin");
    }

    public Path headBytes() throws IOException {
        Revision h = head().orElseThrow(() -> new IOException("no library revision"));
        return bytes(h.id());
    }

    private Path revisionsDir() {
        return root.resolve("revisions");
    }

    private Revision readMeta(String id) throws IOException {
        Path meta = revisionsDir().resolve(id + ".json");
        if (!Files.isRegularFile(meta)) {
            throw new IOException("unknown library revision: " + id);
        }
        return parseMeta(Files.readString(meta, StandardCharsets.UTF_8));
    }

    private static Revision parseMeta(String json) {
        org.json.JSONObject o = new org.json.JSONObject(json);
        return new Revision(
                o.optString("id"),
                o.optString("parentId"),
                o.optString("source"),
                o.optString("author"),
                Instant.parse(o.optString("createdAt", Instant.EPOCH.toString())),
                o.optString("sha256"));
    }

    private static String toJson(Revision rev) {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id", rev.id());
        o.put("parentId", rev.parentId());
        o.put("source", rev.source());
        o.put("author", rev.author());
        o.put("createdAt", rev.createdAt().toString());
        o.put("sha256", rev.sha256());
        return o.toString(2);
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(body));
        }
    }
}
