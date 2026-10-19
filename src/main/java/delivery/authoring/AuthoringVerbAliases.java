package delivery.authoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Owner aliases for {@link StepIntentBinder#classify}: extra words onto click / type / assert / ignore.
 * Built-in classify tokens stay read-only.
 */
public final class AuthoringVerbAliases {
    public static final int MAX = 50;
    private static final Pattern WORD = Pattern.compile("^[a-z]{3,40}$");
    private static final Set<String> TREAT_AS = Set.of("click", "type", "assert", "ignore");
    private static final Set<String> BUILTIN_WORDS = builtinWordSet();

    private static volatile List<Alias> PROCESS = seed();
    private static final ThreadLocal<List<Alias>> ACTIVE = new ThreadLocal<>();

    private AuthoringVerbAliases() {
    }

    public record Alias(String word, String treatAs) {
    }

    public record Builtin(String word, String treatAs) {
    }

    public static List<Alias> seed() {
        return List.of(new Alias("observe", "assert"));
    }

    public static List<Builtin> builtIns() {
        List<Builtin> out = new ArrayList<>();
        out.add(new Builtin("click", "click"));
        out.add(new Builtin("press", "click"));
        out.add(new Builtin("tap", "click"));
        out.add(new Builtin("open", "click"));
        out.add(new Builtin("go to", "click"));
        out.add(new Builtin("add", "click"));
        out.add(new Builtin("enter", "type"));
        out.add(new Builtin("type", "type"));
        out.add(new Builtin("fill", "type"));
        out.add(new Builtin("input", "type"));
        out.add(new Builtin("select", "type"));
        out.add(new Builtin("choose", "type"));
        out.add(new Builtin("pick", "type"));
        out.add(new Builtin("leave/keep empty", "type"));
        out.add(new Builtin("leave blank", "type"));
        out.add(new Builtin("do not fill", "type"));
        out.add(new Builtin("skip the field", "type"));
        out.add(new Builtin("confirm", "assert"));
        out.add(new Builtin("verify", "assert"));
        out.add(new Builtin("shown", "assert"));
        out.add(new Builtin("visible", "assert"));
        out.add(new Builtin("see", "assert"));
        out.add(new Builtin("displays", "assert"));
        out.add(new Builtin("is shown", "assert"));
        out.add(new Builtin("are visible", "assert"));
        out.add(new Builtin("post-login…", "ignore"));
        out.add(new Builtin("do not … (start of step)", "ignore"));
        return List.copyOf(out);
    }

    public static List<String> codeOnlySpecials() {
        return List.of(
                "username/email + type/fill on a login line → type user",
                "password + type/fill → type password",
                "login/sign in + click/press/button → click login",
                "explicit capture from / compare captured / assert signed-out lines");
    }

    public static boolean isBuiltinWord(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        return BUILTIN_WORDS.contains(word.trim().toLowerCase(Locale.ROOT));
    }

    public static List<Alias> normalize(List<Alias> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX) {
            throw new IllegalArgumentException("At most " + MAX + " verb aliases");
        }
        Map<String, Alias> unique = new LinkedHashMap<>();
        for (Alias row : raw) {
            if (row == null) {
                continue;
            }
            String word = row.word() == null ? "" : row.word().trim().toLowerCase(Locale.ROOT);
            String treat = row.treatAs() == null ? "" : row.treatAs().trim().toLowerCase(Locale.ROOT);
            if (!WORD.matcher(word).matches()) {
                throw new IllegalArgumentException(
                        "Alias word must be 3–40 letters (got '" + (row.word() == null ? "" : row.word()) + "')");
            }
            if (!TREAT_AS.contains(treat)) {
                throw new IllegalArgumentException("treatAs must be click, type, assert, or ignore");
            }
            if (isBuiltinWord(word)) {
                throw new IllegalArgumentException("'" + word + "' is a built-in verb and cannot be aliased");
            }
            if (unique.containsKey(word)) {
                throw new IllegalArgumentException("Duplicate alias word: " + word);
            }
            unique.put(word, new Alias(word, treat));
        }
        return List.copyOf(unique.values());
    }

    public static List<Alias> fromRaw(Object raw) {
        if (raw == null) {
            return seed();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("authoringVerbAliases must be a list");
        }
        List<Alias> parsed = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Each alias must be {word, treatAs}");
            }
            Object w = map.get("word");
            Object t = map.get("treatAs");
            parsed.add(new Alias(w == null ? "" : String.valueOf(w), t == null ? "" : String.valueOf(t)));
        }
        return normalize(parsed);
    }

    public static List<Map<String, String>> toMaps(List<Alias> aliases) {
        List<Map<String, String>> out = new ArrayList<>();
        if (aliases == null) {
            return out;
        }
        for (Alias a : aliases) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("word", a.word());
            row.put("treatAs", a.treatAs());
            out.add(row);
        }
        return out;
    }

    public static List<Map<String, String>> builtInMaps() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Builtin b : builtIns()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("word", b.word());
            row.put("treatAs", b.treatAs());
            out.add(row);
        }
        return out;
    }

    public static Scope activate(List<Alias> aliases) {
        List<Alias> previous = ACTIVE.get();
        ACTIVE.set(aliases == null ? List.of() : List.copyOf(aliases));
        return new Scope(previous);
    }

    public static void publish(List<Alias> aliases) {
        PROCESS = aliases == null ? seed() : List.copyOf(aliases);
    }

    public static List<Alias> current() {
        List<Alias> thread = ACTIVE.get();
        return thread != null ? thread : PROCESS;
    }

    public static List<Alias> processSnapshot() {
        return PROCESS;
    }

    public static StepIntentBinder.IntentKind classify(String lower) {
        if (lower == null || lower.isBlank()) {
            return null;
        }
        for (Alias alias : current()) {
            if (alias == null || !containsWord(lower, alias.word())) {
                continue;
            }
            return switch (alias.treatAs()) {
                case "assert" -> StepIntentBinder.IntentKind.ASSERT_VISIBLE;
                case "click" -> StepIntentBinder.IntentKind.CLICK;
                case "type" -> StepIntentBinder.IntentKind.TYPE_FIELD;
                default -> null;
            };
        }
        return null;
    }

    public static boolean looksLikeAssert(String lower) {
        return matchesTreatAs(lower, "assert");
    }

    public static boolean matchesTreatAs(String lower, String treatAs) {
        if (lower == null || treatAs == null) {
            return false;
        }
        for (Alias alias : current()) {
            if (alias != null && treatAs.equals(alias.treatAs()) && containsWord(lower, alias.word())) {
                return true;
            }
        }
        return false;
    }

    static boolean containsWord(String lower, String word) {
        if (lower == null || word == null || word.isBlank()) {
            return false;
        }
        int from = 0;
        while (from <= lower.length() - word.length()) {
            int at = lower.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            boolean startOk = at == 0 || !Character.isLetterOrDigit(lower.charAt(at - 1));
            int end = at + word.length();
            boolean endOk = end == lower.length() || !Character.isLetterOrDigit(lower.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    public static final class Scope implements AutoCloseable {
        private final List<Alias> previous;
        private final boolean hadPrevious;

        private Scope(List<Alias> previous) {
            this.previous = previous;
            this.hadPrevious = previous != null;
        }

        @Override
        public void close() {
            if (hadPrevious) {
                ACTIVE.set(previous);
            } else {
                ACTIVE.remove();
            }
        }
    }

    private static Set<String> builtinWordSet() {
        Set<String> out = new LinkedHashSet<>();
        for (String w : List.of(
                "click", "press", "tap", "open", "add",
                "enter", "type", "fill", "input", "select", "choose", "pick",
                "confirm", "verify", "shown", "visible", "see", "displays",
                "submit")) {
            out.add(w);
        }
        return Set.copyOf(out);
    }
}
