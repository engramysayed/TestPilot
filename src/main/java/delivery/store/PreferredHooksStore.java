package delivery.store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Site-level preferred locator attribute names, stored next to domain locator memory.
 */
public final class PreferredHooksStore {
    public static final String FILE_NAME = "preferred-hooks.json";
    private static final int MAX_HOOKS = 8;
    private static final Pattern ATTR = Pattern.compile("^[a-z][a-z0-9_-]{0,62}$");
    private static final ThreadLocal<List<String>> ACTIVE = ThreadLocal.withInitial(List::of);

    private PreferredHooksStore() {
    }

    public static Path file(Path storeRoot, String baseUrl) {
        Path domain = DomainStorePaths.resolveDomainRoot(storeRoot, baseUrl);
        return domain == null ? null : domain.resolve(FILE_NAME);
    }

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split("[,\\n]")) {
            String name = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
            if (name.isBlank() || !ATTR.matcher(name).matches()) {
                continue;
            }
            out.add(name);
            if (out.size() >= MAX_HOOKS) {
                break;
            }
        }
        return List.copyOf(out);
    }

    public static String join(List<String> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            return "";
        }
        return String.join(", ", hooks);
    }

    public static List<String> load(Path storeRoot, String baseUrl) {
        Path file = file(storeRoot, baseUrl);
        if (file == null || !Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            JSONObject root = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            JSONArray arr = root.optJSONArray("attributes");
            if (arr == null) {
                return List.of();
            }
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) {
                    raw.append(',');
                }
                raw.append(arr.optString(i, ""));
            }
            return parse(raw.toString());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static void save(Path storeRoot, String baseUrl, String raw) {
        List<String> hooks = parse(raw);
        Path file = file(storeRoot, baseUrl);
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (String hook : hooks) {
                arr.put(hook);
            }
            root.put("attributes", arr);
            Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Settings must not fail the project rename if disk write is best-effort.
        }
    }

    public static Scope activate(List<String> hooks) {
        List<String> previous = List.copyOf(current());
        ACTIVE.set(hooks == null || hooks.isEmpty() ? List.of() : List.copyOf(hooks));
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final List<String> previous;

        private Scope(List<String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            ACTIVE.set(previous);
        }
    }

    public static List<String> current() {
        List<String> hooks = ACTIVE.get();
        return hooks == null ? List.of() : hooks;
    }

    public static boolean matches(String locatorValue, List<String> hooks) {
        if (locatorValue == null || locatorValue.isBlank() || hooks == null || hooks.isEmpty()) {
            return false;
        }
        String hay = locatorValue.toLowerCase(Locale.ROOT);
        for (String hook : hooks) {
            if (hook != null && !hook.isBlank() && hay.contains(hook.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
