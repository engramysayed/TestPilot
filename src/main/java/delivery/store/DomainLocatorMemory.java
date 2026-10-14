package delivery.store;

import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Proven locators keyed by registrable host + Excel open-path + intent.
 * Tried before bind; dropped on execute failure; overwritten on success.
 */
public class DomainLocatorMemory {
    public static final String FILE_NAME = "domain-locator-memory.json";

    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final Map<String, Slot> pending = new LinkedHashMap<>();
    private final java.util.Set<String> removed = new java.util.HashSet<>();

    public static Path sharedFile(Path storeRoot, String baseUrl) {
        Path domain = DomainStorePaths.resolveDomainRoot(storeRoot, baseUrl);
        return domain == null ? null : domain.resolve(FILE_NAME);
    }

    public static Path sharedFile(Path storeRoot, delivery.identity.TenantId tenant, String baseUrl) {
        if (tenant == null) {
            return sharedFile(storeRoot, baseUrl);
        }
        if (storeRoot == null) {
            return null;
        }
        return delivery.identity.ScopePaths.siteRoot(storeRoot, tenant, baseUrl).resolve(FILE_NAME);
    }

    /**
     * One file per site. Older per-project copies are merged only when the shared
     * file does not exist yet, so a forgotten slot is not resurrected later.
     */
    public Path openShared(Path storeRoot, String baseUrl) {
        Path shared = sharedFile(storeRoot, baseUrl);
        if (shared == null) {
            return null;
        }
        if (Files.isRegularFile(shared)) {
            load(shared);
            return shared;
        }
        Path domain = shared.getParent();
        if (domain != null && Files.isDirectory(domain)) {
            try (var children = Files.list(domain)) {
                children.filter(Files::isDirectory)
                        .map(p -> p.resolve(FILE_NAME))
                        .filter(Files::isRegularFile)
                        .forEach(this::load);
            } catch (Exception ignored) {
                // Best-effort migration; prove still runs.
            }
        }
        return shared;
    }

    /**
     * Tenant-scoped site memory. Does not merge the legacy domain-shared file.
     */
    public Path openShared(Path storeRoot, delivery.identity.TenantId tenant, String baseUrl) {
        Path shared = sharedFile(storeRoot, tenant, baseUrl);
        if (shared == null) {
            return null;
        }
        if (Files.isRegularFile(shared)) {
            load(shared);
        }
        return shared;
    }

    public static String intentKey(StepIntentBinder.IntentLine intent) {
        if (intent == null || intent.kind() == null) {
            return "";
        }
        String text = intent.text() == null ? "" : intent.text().trim().toLowerCase(Locale.ROOT);
        return intent.kind().name() + "|" + text;
    }

    public void remember(String host, String excelPath, StepIntentBinder.IntentLine intent, ProvenStep step) {
        // Locator-only memory cannot round-trip assertion semantics, captures or expected values.
        if (intent != null && intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) return;
        if (intent == null || step == null || step.locatorValue() == null || step.locatorValue().isBlank()) {
            return;
        }
        if (!locatorFitsIntent(intent, step.locatorValue())) {
            return;
        }
        if (StepIntentBinder.wantsFormSubmit(intent.text())
                && (StepIntentBinder.looksLikeNonSubmitNavigationLocator(step.locatorValue())
                || !looksLikeReusableSubmitLocator(step.locatorValue()))) {
            return;
        }
        String key = slotKey(host, excelPath, intentKey(intent));
        slots.put(key, new Slot(
                normalizeHost(host),
                normalizePath(excelPath),
                intentKey(intent),
                step.locatorStrategy() == null ? "" : step.locatorStrategy(),
                step.locatorValue(),
                step.action() == null ? "" : step.action()));
        pending.put(key, slots.get(key));
        removed.remove(key);
    }

    public Optional<ProvenStep> recall(
            String host, String excelPath, StepIntentBinder.IntentLine intent, String tcId) {
        if (intent == null) {
            return Optional.empty();
        }
        if (intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) {
            return recallFieldAssertion(host, excelPath, intent, tcId);
        }
        Slot slot = slots.get(slotKey(host, excelPath, intentKey(intent)));
        if (slot == null) {
            return Optional.empty();
        }
        if (!locatorFitsIntent(intent, slot.locator())) {
            slots.remove(slotKey(host, excelPath, intentKey(intent)));
            return Optional.empty();
        }
        if (StepIntentBinder.wantsFormSubmit(intent.text())
                && StepIntentBinder.looksLikeNonSubmitNavigationLocator(slot.locator())) {
            return Optional.empty();
        }
        return Optional.of(new ProvenStep(
                tcId == null ? "" : tcId,
                "Page",
                "elementAction",
                slot.action(),
                slot.strategy(),
                slot.locator(),
                "",
                "",
                "",
                true,
                "memory:" + slot.intentKey() + fieldSuffix(intent)));
    }

    private static String fieldSuffix(StepIntentBinder.IntentLine intent) {
        String field = StepIntentBinder.intentFieldPhrase(intent == null ? null : intent.text());
        if (field == null || field.isBlank()) {
            return "";
        }
        return ":field=" + field.trim().replaceAll("\\s+", "_");
    }

    private Optional<ProvenStep> recallFieldAssertion(String host, String path,
            StepIntentBinder.IntentLine intent, String tcId) {
        var match = java.util.regex.Pattern.compile(
                "(?i)^(?:confirm|verify|assert)\\s+the\\s+(.+?)\\s+field\\s+is\\s+(not\\s+)?visible[.!]?$")
                .matcher(intent.text().trim());
        if (!match.matches()) return Optional.empty(); // Compound and text assertions must bind normally.
        String field = match.group(1).trim();
        Map<String, Slot> matches = new LinkedHashMap<>();
        for (Slot candidate : slots.values()) {
            if (!candidate.host().equals(normalizeHost(host)) || !candidate.path().equals(normalizePath(path))
                    || !"type".equals(candidate.action()) || !candidate.intentKey().startsWith("TYPE_")) continue;
            String text = candidate.intentKey().substring(candidate.intentKey().indexOf('|') + 1);
            String name = StepIntentBinder.intentFieldPhrase(text);
            if (name == null || !field.equalsIgnoreCase(name.trim())) continue;
            String selector = candidate.locator();
            if ("css".equals(candidate.strategy())) selector = selector.replaceFirst("^input(?=\\[)", "");
            String key = candidate.strategy() + "|" + selector;
            Slot existing = matches.get(key);
            if (existing == null || candidate.locator().startsWith("input[")) matches.put(key, candidate);
        }
        if (matches.size() != 1) return Optional.empty();
        Slot target = matches.values().iterator().next();
        return Optional.of(new ProvenStep(tcId, "Page", "elementAction", "assert", target.strategy(),
                target.locator(), "", match.group(2) == null ? "visible" : "notVisible", "", true,
                "memory:field-assertion"));
    }

    public void forget(String host, String excelPath, StepIntentBinder.IntentLine intent) {
        String key = slotKey(host, excelPath, intentKey(intent));
        slots.remove(key);
        pending.remove(key);
        removed.add(key);
    }

    public void load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JSONObject root = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            JSONArray entries = root.optJSONArray("entries");
            if (entries == null) {
                return;
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject o = entries.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String host = o.optString("host", "");
                String path = o.optString("path", "");
                String intentKey = o.optString("intentKey", "");
                if (intentKey.isBlank()) {
                    continue;
                }
                String locator = o.optString("locator", "");
                if (!intentKeyFitsLocator(intentKey, locator)) {
                    continue;
                }
                slots.put(slotKey(host, path, intentKey), new Slot(
                        normalizeHost(host),
                        normalizePath(path),
                        intentKey,
                        o.optString("strategy", ""),
                        locator,
                        o.optString("action", "")));
            }
        } catch (Exception ignored) {
            // Corrupt memory must not block prove; start empty.
        }
    }

    public void save(Path file) {
        if (file == null) {
            return;
        }
        try {
            delivery.identity.PublicationLock.run(file.toAbsolutePath().resolveSibling(file.getFileName() + ".lock"), () -> {
            DomainLocatorMemory current = new DomainLocatorMemory();
            current.load(file);
            // Apply only this writer's changes; an older job must not erase another job's entries.
            if (!Files.exists(file)) current.slots.putAll(slots);
            removed.forEach(current.slots::remove);
            current.slots.putAll(pending);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            JSONObject root = new JSONObject();
            JSONArray entries = new JSONArray();
            for (Slot slot : current.slots.values()) {
                JSONObject o = new JSONObject();
                o.put("host", slot.host());
                o.put("path", slot.path());
                o.put("intentKey", slot.intentKey());
                o.put("strategy", slot.strategy());
                o.put("locator", slot.locator());
                o.put("action", slot.action());
                entries.put(o);
            }
            root.put("entries", entries);
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "locator-memory-", ".tmp");
            try {
                Files.writeString(temp, root.toString(2), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
            slots.clear();
            slots.putAll(current.slots);
            pending.clear();
            removed.clear();
            return null;
            });
        } catch (Exception ignored) {
            utils.LogsManager.warn("LOCATOR_MEMORY: persistence failed; changes retained for retry ("
                    + ignored.getClass().getSimpleName() + ")");
        }
    }

    private static boolean looksLikeReusableSubmitLocator(String locator) {
        if (locator == null) {
            return false;
        }
        String loc = locator.toLowerCase(Locale.ROOT);
        return loc.contains("submit") || loc.contains("websubmit")
                || loc.contains("sign-up") || loc.contains("signup") || loc.contains("sign up")
                || loc.contains("create account") || loc.contains("register");
    }

    /**
     * Auth-page controls must not be remembered for cart/product/assert intents.
     * (SauceDemo pollution: every CLICK recalled {@code login-button}.)
     */
    static boolean locatorFitsIntent(StepIntentBinder.IntentLine intent, String locator) {
        if (intent == null || intent.kind() == null) {
            return false;
        }
        return intentKeyFitsLocator(intentKey(intent), locator);
    }

    static boolean intentKeyFitsLocator(String intentKey, String locator) {
        if (intentKey == null || intentKey.isBlank() || locator == null || locator.isBlank()) {
            return false;
        }
        String key = intentKey.toLowerCase(Locale.ROOT);
        if (key.startsWith("assert_visible|")) return false;
        String loc = locator.toLowerCase(Locale.ROOT);
        boolean authLocator = looksLikeAuthControlLocator(loc);
        boolean loginIntent = key.startsWith("type_user|")
                || key.startsWith("type_pass|")
                || key.startsWith("click_login|");
        if (key.startsWith("click_login|")) {
            return looksLikeLoginSubmitLocator(loc);
        }
        if (loginIntent) {
            return authLocator;
        }
        if (key.startsWith("click|") && looksLikeTypedFieldLocator(loc)) {
            return false;
        }
        // Non-login intents must never reuse login/username/password controls
        return !authLocator;
    }

    static boolean looksLikeTypedFieldLocator(String locator) {
        if (locator == null || locator.isBlank()) {
            return false;
        }
        String loc = locator.toLowerCase(Locale.ROOT);
        String compact = loc.replaceAll("[^a-z0-9]+", "");
        if (compact.contains("button") || compact.contains("submit")) {
            return false;
        }
        return compact.contains("otp")
                || compact.contains("username")
                || compact.contains("password")
                || compact.contains("email");
    }

    static boolean looksLikeLoginSubmitLocator(String locatorLower) {
        if (locatorLower == null || locatorLower.isBlank()) {
            return false;
        }
        String loc = locatorLower.toLowerCase(Locale.ROOT);
        String compact = loc.replaceAll("[^a-z0-9]+", "");
        if (compact.contains("password") || compact.contains("username")
                || compact.contains("otp")) {
            return false;
        }
        if (compact.contains("signin") || compact.contains("loginbutton")
                || compact.contains("submit") || loc.contains("sign_in")
                || loc.contains("sign-in")) {
            return true;
        }
        return compact.contains("login") && compact.contains("button");
    }

    static boolean looksLikeAuthControlLocator(String locatorLower) {
        if (locatorLower == null || locatorLower.isBlank()) {
            return false;
        }
        String loc = locatorLower.toLowerCase(Locale.ROOT);
        return loc.contains("login-button")
                || loc.contains("login_button")
                || loc.equals("login")
                || loc.contains("login-container")
                || loc.contains("user-name")
                || loc.contains("username")
                || loc.contains("user_name")
                || loc.equals("password")
                || loc.contains("password")
                || loc.contains("signin")
                || loc.contains("sign-in");
    }

    private static String slotKey(String host, String path, String intentKey) {
        return normalizeHost(host) + "\n" + normalizePath(path) + "\n" + (intentKey == null ? "" : intentKey);
    }

    private static String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.toLowerCase(Locale.ROOT);
    }

    private record Slot(
            String host,
            String path,
            String intentKey,
            String strategy,
            String locator,
            String action
    ) {
    }
}
