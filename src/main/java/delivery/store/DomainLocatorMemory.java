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
    private final Map<String, Slot> slots = new LinkedHashMap<>();

    public static String intentKey(StepIntentBinder.IntentLine intent) {
        if (intent == null || intent.kind() == null) {
            return "";
        }
        String text = intent.text() == null ? "" : intent.text().trim().toLowerCase(Locale.ROOT);
        return intent.kind().name() + "|" + text;
    }

    public void remember(String host, String excelPath, StepIntentBinder.IntentLine intent, ProvenStep step) {
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
    }

    public Optional<ProvenStep> recall(
            String host, String excelPath, StepIntentBinder.IntentLine intent, String tcId) {
        if (intent == null) {
            return Optional.empty();
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

    public void forget(String host, String excelPath, StepIntentBinder.IntentLine intent) {
        slots.remove(slotKey(host, excelPath, intentKey(intent)));
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
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            JSONObject root = new JSONObject();
            JSONArray entries = new JSONArray();
            for (Slot slot : slots.values()) {
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
            Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Persistence is best-effort; prove still succeeds without it.
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
        String loc = locator.toLowerCase(Locale.ROOT);
        boolean authLocator = looksLikeAuthControlLocator(loc);
        boolean loginIntent = key.startsWith("type_user|")
                || key.startsWith("type_pass|")
                || key.startsWith("click_login|");
        if (loginIntent) {
            return authLocator || key.startsWith("click_login|");
        }
        // Non-login intents must never reuse login/username/password controls
        return !authLocator;
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
