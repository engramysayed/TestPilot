package delivery.env;

import delivery.identity.PublicationLock;
import delivery.store.StaleLibraryRevisionException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable environment revisions. Changing origin does not rename project storage.
 */
public final class EnvironmentStore {
    public record Revision(
            String id,
            String parentId,
            String name,
            Instant createdAt,
            String sha256,
            EnvironmentProfile profile
    ) {
    }

    private static final String HEAD = "HEAD";
    private final Path root;

    public EnvironmentStore(Path root) {
        this.root = root;
    }

    public Optional<Revision> head() throws Exception {
        return PublicationLock.call(lockFile(), () -> {
            Path head = root.resolve(HEAD);
            if (!Files.isRegularFile(head)) {
                return Optional.empty();
            }
            return Optional.of(readMeta(Files.readString(head, StandardCharsets.UTF_8).trim()));
        });
    }

    public List<Revision> list() throws Exception {
        return PublicationLock.call(lockFile(), () -> {
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
        });
    }

    public Revision commit(String expectedBaseId, EnvironmentProfile profile) throws Exception {
        if (profile == null || profile.name().isBlank() || profile.origin().isBlank()) {
            throw new IllegalArgumentException("environment name and origin are required");
        }
        return PublicationLock.call(lockFile(), () -> {
            Optional<Revision> current = readHeadUnlocked();
            String headId = current.map(Revision::id).orElse(null);
            String expected = expectedBaseId == null || expectedBaseId.isBlank() ? headId : expectedBaseId;
            if (headId != null && (expected == null || !headId.equals(expected))) {
                throw new StaleLibraryRevisionException(expected == null ? "" : expected, headId);
            }
            if (headId == null && expected != null && !expected.isBlank()) {
                throw new StaleLibraryRevisionException(expected, "");
            }
            String id = "envrev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            byte[] body = profile.toJson().toString().getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(revisionsDir());
            Files.write(revisionsDir().resolve(id + ".bin"), body);
            Revision rev = new Revision(
                    id,
                    headId == null ? "" : headId,
                    profile.name(),
                    Instant.now(),
                    sha256(body),
                    profile);
            Files.writeString(revisionsDir().resolve(id + ".json"), toJson(rev), StandardCharsets.UTF_8);
            Files.writeString(root.resolve(HEAD), id, StandardCharsets.UTF_8);
            return rev;
        });
    }

    public Revision read(String revisionId) throws Exception {
        return PublicationLock.call(lockFile(), () -> readMeta(revisionId));
    }

    public static Map<String, String> compare(EnvironmentProfile before, EnvironmentProfile after) {
        EnvironmentProfile left = before == null ? new EnvironmentProfile("", "", "", "", 0, 0) : before;
        EnvironmentProfile right = after == null ? new EnvironmentProfile("", "", "", "", 0, 0) : after;
        Map<String, String> changed = new LinkedHashMap<>();
        putIfChanged(changed, "origin", left.origin(), right.origin());
        putIfChanged(changed, "credentialProfileRef", left.credentialProfileRef(), right.credentialProfileRef());
        putIfChanged(changed, "providerAllowlist", left.providerAllowlist(), right.providerAllowlist());
        putIfChanged(changed, "maxQueued", String.valueOf(left.maxQueued()), String.valueOf(right.maxQueued()));
        putIfChanged(changed, "maxRunning", String.valueOf(left.maxRunning()), String.valueOf(right.maxRunning()));
        return Map.copyOf(changed);
    }

    private Optional<Revision> readHeadUnlocked() throws Exception {
        Path head = root.resolve(HEAD);
        if (!Files.isRegularFile(head)) {
            return Optional.empty();
        }
        return Optional.of(readMeta(Files.readString(head, StandardCharsets.UTF_8).trim()));
    }

    private Path revisionsDir() {
        return root.resolve("revisions");
    }

    private Path lockFile() {
        return root.resolve("environments.lock");
    }

    private Revision readMeta(String id) throws Exception {
        Path meta = revisionsDir().resolve(id + ".json");
        if (!Files.isRegularFile(meta)) {
            throw new IllegalArgumentException("unknown environment revision: " + id);
        }
        return parseMeta(Files.readString(meta, StandardCharsets.UTF_8));
    }

    private static Revision parseMeta(String json) {
        org.json.JSONObject o = new org.json.JSONObject(json);
        return new Revision(
                o.optString("id"),
                o.optString("parentId"),
                o.optString("name"),
                Instant.parse(o.optString("createdAt", Instant.EPOCH.toString())),
                o.optString("sha256"),
                EnvironmentProfile.fromJson(o.optJSONObject("profile")));
    }

    private static String toJson(Revision rev) {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id", rev.id());
        o.put("parentId", rev.parentId());
        o.put("name", rev.name());
        o.put("createdAt", rev.createdAt().toString());
        o.put("sha256", rev.sha256());
        o.put("profile", rev.profile().toJson());
        return o.toString(2);
    }

    private static void putIfChanged(Map<String, String> changed, String field, String before, String after) {
        if (!java.util.Objects.equals(before, after)) {
            changed.put(field + ".before", before);
            changed.put(field + ".after", after);
        }
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }
}
