package delivery.mobile.codegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted naming map — pins symbols by identity key.
 * Reordering cases / UPDATE title changes must not rename unchanged identities.
 */
public final class NamingMap {

    private final Map<String, String> identityToSymbol;

    public NamingMap(Map<String, String> identityToSymbol) {
        this.identityToSymbol = new LinkedHashMap<>(Objects.requireNonNull(identityToSymbol));
    }

    public static NamingMap empty() {
        return new NamingMap(Map.of());
    }

    public static NamingMap load(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return empty();
        }
        ObjectMapper m = mapper();
        try (Reader r = Files.newBufferedReader(path)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = m.readValue(r, Map.class);
            Object entries = root.get("entries");
            Map<String, String> out = new LinkedHashMap<>();
            if (entries instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
            return new NamingMap(out);
        }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ObjectMapper m = mapper();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", "keel.mobile-name-map/v1");
        root.put("entries", identityToSymbol);
        try (Writer w = Files.newBufferedWriter(path)) {
            m.writerWithDefaultPrettyPrinter().writeValue(w, root);
        }
    }

    public String get(String identityKey) {
        return identityToSymbol.get(identityKey);
    }

    public String pin(String identityKey, String proposedSymbol) {
        String existing = identityToSymbol.get(identityKey);
        if (existing != null) {
            return existing;
        }
        String symbol = proposedSymbol;
        // Collision on symbol value → append short hash of identity
        if (identityToSymbol.containsValue(symbol)) {
            symbol = proposedSymbol + "_" + MobileCodegenNaming.shortIdentityHash(identityKey);
        }
        identityToSymbol.put(identityKey, symbol);
        return symbol;
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(identityToSymbol);
    }

    private static ObjectMapper mapper() {
        ObjectMapper m = new ObjectMapper();
        m.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return m;
    }
}
