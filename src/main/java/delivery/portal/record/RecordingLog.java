package delivery.portal.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns page events into the lines stored on a test case.
 * Password values are never copied into a line.
 */
public final class RecordingLog {
    public static final int MAX_STEPS = 80;

    public record Event(String kind, String label, String value, boolean secret, String strategy, String locator) {
        public Event(String kind, String label, String value, boolean secret) {
            this(kind, label, value, secret, "", "");
        }
    }

    /** One remembered action, including the locator captured in Chrome. */
    public record Capture(
            String kind,
            String label,
            String value,
            boolean secret,
            String strategy,
            String locator,
            String pageUrl
    ) {
    }

    private final List<String> committed = new ArrayList<>();
    private final List<Capture> committedCaptures = new ArrayList<>();
    private List<String> pageSteps = List.of();
    private List<Event> pageEvents = List.of();
    private String generation = "";
    private String lastUrl = "";

    public synchronized List<String> accept(String generationId, List<Event> events, String url) {
        String gen = generationId == null ? "" : generationId;
        String location = httpUrl(url);
        if (!gen.isEmpty() && !gen.equals(generation)) {
            if (!generation.isEmpty()) {
                committed.addAll(pageSteps);
                committedCaptures.addAll(stamp(pageEvents, lastUrl));
            }
            generation = gen;
            if (!location.isBlank() && !location.equals(lastUrl)) {
                committed.add("Open " + location);
                committedCaptures.add(new Capture("open", location, "", false, "", "", location));
                lastUrl = location;
            }
        } else if (!location.isBlank()) {
            lastUrl = location;
        }
        pageEvents = events == null ? List.of() : List.copyOf(events);
        pageSteps = formatAll(pageEvents);
        return steps();
    }

    public synchronized List<Capture> captures() {
        List<Capture> all = new ArrayList<>(committedCaptures.size() + pageEvents.size());
        all.addAll(committedCaptures);
        all.addAll(stamp(pageEvents, lastUrl));
        if (all.size() > MAX_STEPS) {
            return List.copyOf(all.subList(all.size() - MAX_STEPS, all.size()));
        }
        return List.copyOf(all);
    }

    public static int locatedCount(List<Capture> captures) {
        int n = 0;
        if (captures == null) {
            return 0;
        }
        for (Capture capture : captures) {
            if (capture != null && capture.locator() != null && !capture.locator().isBlank()
                    && !"open".equalsIgnoreCase(capture.kind())) {
                n++;
            }
        }
        return n;
    }

    public static int checkCount(List<Capture> captures) {
        int n = 0;
        if (captures == null) {
            return 0;
        }
        for (Capture capture : captures) {
            if (capture == null || capture.kind() == null) {
                continue;
            }
            String kind = capture.kind().toLowerCase(Locale.ROOT);
            if ("assert".equals(kind) || "assert-url".equals(kind)) {
                n++;
            }
        }
        return n;
    }

    public synchronized List<String> steps() {
        List<String> all = new ArrayList<>(committed.size() + pageSteps.size());
        all.addAll(committed);
        all.addAll(pageSteps);
        if (all.size() > MAX_STEPS) {
            return List.copyOf(all.subList(all.size() - MAX_STEPS, all.size()));
        }
        return List.copyOf(all);
    }

    public static String numbered(List<String> steps) {
        StringBuilder out = new StringBuilder();
        int n = 1;
        if (steps == null) {
            return "";
        }
        for (String step : steps) {
            if (step == null || step.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(n++).append(". ").append(step.trim());
        }
        return out.toString();
    }

    private static List<String> formatAll(List<Event> events) {
        List<String> out = new ArrayList<>();
        String previous = "";
        for (Event event : events) {
            String line = format(event);
            if (line == null || line.isBlank() || line.equals(previous)) {
                continue;
            }
            previous = line;
            out.add(line);
        }
        return List.copyOf(out);
    }

    static String format(Event event) {
        if (event == null || event.kind() == null) {
            return null;
        }
        String kind = event.kind().trim().toLowerCase(Locale.ROOT);
        String label = clean(event.label(), 80);
        String value = clean(event.value(), 180);
        boolean secret = event.secret() || ("type".equals(kind) && passwordLabel(label));
        return switch (kind) {
            case "open" -> {
                String target = httpUrl(label.isBlank() ? value : label);
                yield target.isBlank() ? null : "Open " + target;
            }
            case "type" -> secret
                    ? "Enter password into the " + quote(label.isBlank() ? "Password" : label) + " field"
                    : (value.isBlank() ? null : "Type " + quote(value) + " into " + quote(label.isBlank() ? "the field" : label));
            case "select" -> value.isBlank() ? null
                    : "Choose " + quote(value) + " in " + quote(label.isBlank() ? "the list" : label);
            case "check" -> "Tick " + quote(label.isBlank() ? "the box" : label);
            case "uncheck" -> "Clear " + quote(label.isBlank() ? "the box" : label);
            case "press" -> "Press " + (label.isBlank() ? "Enter" : label);
            case "assert" -> {
                String text = clean(event.label(), 160);
                yield text.isBlank() ? null : "Confirm the text " + quote(text) + " is visible";
            }
            case "assert-url" -> {
                String target = httpUrl(value.isBlank() ? label : value);
                yield target.isBlank() ? null : "Confirm the address contains " + addressHint(target);
            }
            default -> "Click " + quote(label.isBlank() ? "the highlighted control" : label);
        };
    }

    private static List<Capture> stamp(List<Event> events, String pageUrl) {
        List<Capture> out = new ArrayList<>();
        if (events == null) {
            return out;
        }
        for (Event event : events) {
            if (event == null || event.kind() == null || event.kind().isBlank()) {
                continue;
            }
            out.add(new Capture(
                    event.kind(),
                    event.label() == null ? "" : event.label(),
                    event.secret() ? "" : (event.value() == null ? "" : event.value()),
                    event.secret(),
                    event.strategy() == null ? "" : event.strategy(),
                    event.locator() == null ? "" : event.locator(),
                    pageUrl == null ? "" : pageUrl));
        }
        return out;
    }

    private static String addressHint(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            if (path != null && !path.isBlank() && !"/".equals(path)) {
                return path;
            }
            String host = uri.getHost();
            return host == null || host.isBlank() ? url : host;
        } catch (RuntimeException e) {
            return url;
        }
    }

    private static boolean passwordLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("passwd") || lower.contains("passcode");
    }

    private static String httpUrl(String raw) {
        String cleaned = RecordingUrls.safeUrl(raw);
        if (cleaned.isBlank()) {
            return "";
        }
        String scheme = cleaned.length() >= 8 ? cleaned.substring(0, 8).toLowerCase(Locale.ROOT) : cleaned.toLowerCase(Locale.ROOT);
        if (scheme.startsWith("https://") || scheme.startsWith("http://")) {
            return cleaned;
        }
        return "";
    }

    private static String quote(String text) {
        return "\"" + clean(text, 180) + "\"";
    }

    private static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace('"', '\'').replaceAll("[\\r\\n\\t]", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > max) {
            return cleaned.substring(0, max);
        }
        return cleaned;
    }
}
