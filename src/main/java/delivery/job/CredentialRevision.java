package delivery.job;

import delivery.store.ProjectStore;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opaque credential revision for UPDATE reuse. The password itself is never hashed
 * into prove-context. A keyed MAC stays on the server store and maps to a random
 * {@code cred_*} identifier. Customer ZIPs must not contain the key, binding, or MAC.
 */
public final class CredentialRevision {
    public static final String BINDING_FILE = "credential-binding.json";
    public static final String KEY_FILE = "credential-mac.key";
    private static final String KEY_DIR = ".keel";
    private static final ConcurrentHashMap<String, String> MEMORY = new ConcurrentHashMap<>();
    private static volatile byte[] processKey;

    private CredentialRevision() {
    }

    public static String resolve(ConversionJobRequest request) {
        if (request == null) {
            return "";
        }
        return resolve(
                request.storeRoot(),
                request.baseUrl(),
                request.projectId(),
                request.username(),
                request.password(),
                request.tenantId());
    }

    public static String resolve(
            Path storeRoot,
            String baseUrl,
            String projectId,
            String username,
            String password,
            delivery.identity.TenantId tenant
    ) {
        boolean bound = (username != null && !username.isBlank())
                || (password != null && !password.isBlank());
        if (!bound) {
            return "";
        }
        byte[] key = loadOrCreateKey(storeRoot);
        String mac = hmac(key, normalize(username) + "\0" + (password == null ? "" : password));
        String memoryId = memoryKey(storeRoot, tenant, projectId, mac);
        Path projectRoot = projectRootOrNull(storeRoot, baseUrl, projectId, tenant);
        if (projectRoot != null) {
            Path bindingFile = projectRoot.resolve(BINDING_FILE);
            String existing = readRevisionIfMacMatches(bindingFile, mac);
            if (existing != null) {
                MEMORY.put(memoryId, existing);
                return existing;
            }
            String minted = mint();
            writeBinding(bindingFile, minted, mac);
            MEMORY.put(memoryId, minted);
            return minted;
        }
        return MEMORY.computeIfAbsent(memoryId, ignored -> mint());
    }

    static boolean persistable(Path storeRoot) {
        if (storeRoot == null) {
            return false;
        }
        Path abs = storeRoot.toAbsolutePath().normalize();
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        return !abs.equals(cwd);
    }

    private static Path projectRootOrNull(
            Path storeRoot, String baseUrl, String projectId, delivery.identity.TenantId tenant
    ) {
        if (!persistable(storeRoot) || projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            return new ProjectStore(storeRoot, baseUrl, tenant).projectRoot(projectId);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] loadOrCreateKey(Path storeRoot) {
        if (persistable(storeRoot)) {
            try {
                Path keyFile = storeRoot.resolve(KEY_DIR).resolve(KEY_FILE);
                if (Files.isRegularFile(keyFile)) {
                    return Files.readAllBytes(keyFile);
                }
                byte[] generated = randomKey();
                Files.createDirectories(keyFile.getParent());
                Files.write(keyFile, generated);
                return generated;
            } catch (Exception ignored) {
                // fall through to process-local key
            }
        }
        if (processKey == null) {
            synchronized (CredentialRevision.class) {
                if (processKey == null) {
                    processKey = randomKey();
                }
            }
        }
        return processKey;
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private static String readRevisionIfMacMatches(Path bindingFile, String mac) {
        if (bindingFile == null || !Files.isRegularFile(bindingFile)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(Files.readString(bindingFile, StandardCharsets.UTF_8));
            String storedMac = json.optString("mac", "");
            String revision = json.optString("revision", "");
            if (!storedMac.isBlank() && storedMac.equals(mac) && revision.startsWith("cred_")) {
                return revision;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void writeBinding(Path bindingFile, String revision, String mac) {
        try {
            Files.createDirectories(bindingFile.getParent());
            JSONObject json = new JSONObject();
            json.put("revision", revision);
            json.put("mac", mac);
            Files.writeString(bindingFile, json.toString(2), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // in-memory map still holds the revision for this process
        }
    }

    private static String hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String mint() {
        return "cred_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String memoryKey(
            Path storeRoot, delivery.identity.TenantId tenant, String projectId, String mac
    ) {
        String store = storeRoot == null ? "" : storeRoot.toAbsolutePath().normalize().toString();
        String tenantPart = tenant == null ? "" : tenant.value();
        return store + "\0" + tenantPart + "\0" + (projectId == null ? "" : projectId) + "\0" + mac;
    }
}
