package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.support.CronExpression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Suite packs SoT: {@code {projectRoot}/suites.json}.
 * Seeds built-in smoke / regression / nightly packs on first read when missing.
 */
public final class SuiteDiskStore {
    public static final String FILE_NAME = "suites.json";
    public static final Set<String> RESERVED_TAGS = Set.of("smoke", "regression", "nightly");
    public static final ZoneId SCHEDULE_ZONE = ZoneId.of("Africa/Cairo");

    private static final Pattern ID = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ObjectMapper mapper;

    public SuiteDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public SuiteDiskStore() {
        this(new ObjectMapper());
    }

    public Path file(Path projectRoot) {
        return projectRoot.resolve(FILE_NAME);
    }

    public List<Map<String, Object>> list(Path projectRoot, String tagFilter) throws IOException {
        List<Map<String, Object>> all = ensureSeeded(projectRoot);
        if (tagFilter == null || tagFilter.isBlank()) {
            return List.copyOf(all);
        }
        String want = tagFilter.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : all) {
            for (String t : tagsOf(row)) {
                if (want.equals(t.toLowerCase(Locale.ROOT))) {
                    out.add(row);
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    public Optional<Map<String, Object>> get(Path projectRoot, String suiteId) throws IOException {
        validateId(suiteId, "suiteId");
        for (Map<String, Object> row : ensureSeeded(projectRoot)) {
            if (suiteId.trim().equals(str(row.get("suiteId")))) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public Map<String, Object> create(
            Path projectRoot,
            String name,
            List<String> tags,
            List<String> tcIds
    ) throws IOException {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        List<String> normalizedTags = normalizeTags(tags, true);
        if (normalizedTags.isEmpty()) {
            throw new BadRequestException("tags must contain at least one tag");
        }
        for (String t : normalizedTags) {
            if (RESERVED_TAGS.contains(t.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("Reserved built-in tag not allowed for user packs: " + t);
            }
        }
        String now = Instant.now().toString();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("suiteId", UUID.randomUUID().toString());
        row.put("name", name.trim());
        row.put("tags", normalizedTags);
        row.put("tcIds", normalizeTcIds(tcIds));
        row.put("builtIn", false);
        row.put("alertChannelId", null);
        row.put("alertMinSeverity", null);
        row.put("schedule", null);
        row.put("monitor", null);
        row.put("createdAt", now);
        row.put("updatedAt", now);

        List<Map<String, Object>> all = new ArrayList<>(ensureSeeded(projectRoot));
        all.add(row);
        writeAll(projectRoot, all);
        return normalize(row);
    }

    public Map<String, Object> update(
            Path projectRoot,
            String suiteId,
            String name,
            List<String> tags,
            List<String> tcIds
    ) throws IOException {
        validateId(suiteId, "suiteId");
        List<Map<String, Object>> all = new ArrayList<>(ensureSeeded(projectRoot));
        boolean found = false;
        Map<String, Object> updated = null;
        for (int i = 0; i < all.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>(all.get(i));
            if (!suiteId.trim().equals(str(row.get("suiteId")))) {
                continue;
            }
            boolean builtIn = bool(row.get("builtIn"));
            if (builtIn) {
                if (name != null && !name.trim().equals(str(row.get("name")))) {
                    throw new BadRequestException("Built-in suite name is immutable");
                }
                if (tags != null) {
                    List<String> incoming = normalizeTags(tags, false);
                    List<String> current = tagsOf(row);
                    if (!sameTagSet(incoming, current)) {
                        throw new BadRequestException("Built-in suite tags are immutable");
                    }
                }
                if (tcIds != null) {
                    row.put("tcIds", normalizeTcIds(tcIds));
                }
            } else {
                if (name != null) {
                    if (name.isBlank()) {
                        throw new BadRequestException("name is required");
                    }
                    row.put("name", name.trim());
                }
                if (tags != null) {
                    List<String> normalizedTags = normalizeTags(tags, true);
                    if (normalizedTags.isEmpty()) {
                        throw new BadRequestException("tags must contain at least one tag");
                    }
                    for (String t : normalizedTags) {
                        if (RESERVED_TAGS.contains(t.toLowerCase(Locale.ROOT))) {
                            throw new BadRequestException("Reserved built-in tag not allowed for user packs: " + t);
                        }
                    }
                    row.put("tags", normalizedTags);
                }
                if (tcIds != null) {
                    row.put("tcIds", normalizeTcIds(tcIds));
                }
            }
            row.put("updatedAt", Instant.now().toString());
            all.set(i, row);
            updated = normalize(row);
            found = true;
            break;
        }
        if (!found) {
            throw new NotFoundException("Suite not found: " + suiteId);
        }
        writeAll(projectRoot, all);
        return updated;
    }

    public void delete(Path projectRoot, String suiteId) throws IOException {
        validateId(suiteId, "suiteId");
        List<Map<String, Object>> all = new ArrayList<>(ensureSeeded(projectRoot));
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            Map<String, Object> row = all.get(i);
            if (suiteId.trim().equals(str(row.get("suiteId")))) {
                if (bool(row.get("builtIn"))) {
                    throw new ConflictException("Built-in suites cannot be deleted");
                }
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            throw new NotFoundException("Suite not found: " + suiteId);
        }
        all.remove(idx);
        writeAll(projectRoot, all);
    }

    public Map<String, Object> putSchedule(
            Path projectRoot,
            String suiteId,
            boolean enabled,
            String kind,
            String cron,
            Integer intervalMinutes
    ) throws IOException {
        validateId(suiteId, "suiteId");
        String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (!"cron".equals(k) && !"interval".equals(k)) {
            throw new BadRequestException("kind must be cron or interval");
        }
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("enabled", enabled);
        schedule.put("kind", k);
        if ("cron".equals(k)) {
            if (cron == null || cron.isBlank()) {
                throw new BadRequestException("cron is required when kind=cron");
            }
            String expr = cron.trim();
            try {
                CronExpression.parse(toSpringCron(expr));
            } catch (Exception e) {
                throw new BadRequestException("Invalid cron expression: " + expr);
            }
            schedule.put("cron", expr);
            schedule.put("intervalMinutes", null);
        } else {
            if (intervalMinutes == null || intervalMinutes < 5) {
                throw new BadRequestException("intervalMinutes must be >= 5");
            }
            schedule.put("cron", null);
            schedule.put("intervalMinutes", intervalMinutes);
        }
        Instant now = Instant.now();
        schedule.put("nextRunAt", enabled ? computeNextRunAt(schedule, now).toString() : null);
        schedule.put("lastFiredAt", null);
        schedule.put("lastJobId", null);

        return mutateSuite(projectRoot, suiteId, row -> {
            // preserve lastFiredAt/lastJobId if rescheduling an existing schedule
            Object existing = row.get("schedule");
            if (existing instanceof Map<?, ?> prev) {
                Object lastFired = prev.get("lastFiredAt");
                Object lastJob = prev.get("lastJobId");
                schedule.put("lastFiredAt", lastFired == null || str(lastFired).isBlank() ? null : str(lastFired));
                schedule.put("lastJobId", lastJob == null || str(lastJob).isBlank() ? null : str(lastJob));
                if (enabled) {
                    schedule.put("nextRunAt", computeNextRunAt(schedule, now).toString());
                }
            }
            row.put("schedule", schedule);
        });
    }

    public Map<String, Object> clearSchedule(Path projectRoot, String suiteId) throws IOException {
        return mutateSuite(projectRoot, suiteId, row -> row.put("schedule", null));
    }

    public Map<String, Object> bindAlert(
            Path projectRoot,
            String suiteId,
            String alertChannelId,
            String alertMinSeverity
    ) throws IOException {
        if (alertChannelId == null || alertChannelId.isBlank()) {
            throw new BadRequestException("alertChannelId is required");
        }
        return mutateSuite(projectRoot, suiteId, row -> {
            row.put("alertChannelId", alertChannelId.trim());
            row.put("alertMinSeverity",
                    alertMinSeverity == null || alertMinSeverity.isBlank() ? null : alertMinSeverity.trim());
        });
    }

    public Map<String, Object> clearAlert(Path projectRoot, String suiteId) throws IOException {
        return mutateSuite(projectRoot, suiteId, row -> {
            row.put("alertChannelId", null);
            row.put("alertMinSeverity", null);
        });
    }

    public Map<String, Object> promoteMonitor(
            Path projectRoot,
            String suiteId,
            String target,
            String fromJobId
    ) throws IOException {
        String t = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (!"prod".equals(t) && !"staging".equals(t)) {
            throw new BadRequestException("target must be prod or staging");
        }
        Map<String, Object> monitor = new LinkedHashMap<>();
        monitor.put("enabled", true);
        monitor.put("target", t);
        monitor.put("promotedAt", Instant.now().toString());
        monitor.put("promotedFromJobId",
                fromJobId == null || fromJobId.isBlank() ? null : fromJobId.trim());
        monitor.put("lastStatus", "unknown");
        monitor.put("lastCheckedAt", null);
        return mutateSuite(projectRoot, suiteId, row -> row.put("monitor", monitor));
    }

    public Map<String, Object> demoteMonitor(Path projectRoot, String suiteId) throws IOException {
        return mutateSuite(projectRoot, suiteId, row -> row.put("monitor", null));
    }

    public Map<String, Object> recordSuiteRunFire(
            Path projectRoot,
            String suiteId,
            String jobId
    ) throws IOException {
        Instant now = Instant.now();
        return mutateSuite(projectRoot, suiteId, row -> {
            Object schedObj = row.get("schedule");
            if (!(schedObj instanceof Map<?, ?>)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> schedule = new LinkedHashMap<>((Map<String, Object>) schedObj);
            schedule.put("lastFiredAt", now.toString());
            schedule.put("lastJobId", jobId);
            if (bool(schedule.get("enabled"))) {
                schedule.put("nextRunAt", computeNextRunAt(schedule, now.plusSeconds(1)).toString());
            }
            row.put("schedule", schedule);
        });
    }

    public List<Map<String, Object>> listMonitors(Path projectRoot) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : ensureSeeded(projectRoot)) {
            Object mon = row.get("monitor");
            if (mon instanceof Map<?, ?> m && bool(m.get("enabled"))) {
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    public boolean isDue(Map<String, Object> suite, Instant now, int withinSeconds) {
        Object schedObj = suite.get("schedule");
        if (!(schedObj instanceof Map<?, ?> sched)) {
            return false;
        }
        if (!bool(sched.get("enabled"))) {
            return false;
        }
        String next = str(sched.get("nextRunAt"));
        if (next.isBlank()) {
            return false;
        }
        try {
            Instant nextAt = Instant.parse(next);
            Instant until = now.plusSeconds(Math.max(0, withinSeconds));
            return !nextAt.isAfter(until);
        } catch (Exception e) {
            return false;
        }
    }

    public static Instant computeNextRunAt(Map<String, Object> schedule, Instant from) {
        String kind = str(schedule.get("kind")).toLowerCase(Locale.ROOT);
        if ("interval".equals(kind)) {
            int minutes = 5;
            Object im = schedule.get("intervalMinutes");
            if (im instanceof Number n) {
                minutes = Math.max(5, n.intValue());
            } else {
                try {
                    minutes = Math.max(5, Integer.parseInt(str(im)));
                } catch (Exception ignored) {
                }
            }
            return from.plusSeconds(minutes * 60L);
        }
        String cron = str(schedule.get("cron"));
        CronExpression expr = CronExpression.parse(toSpringCron(cron));
        ZonedDateTime zdt = from.atZone(SCHEDULE_ZONE);
        ZonedDateTime next = expr.next(zdt);
        if (next == null) {
            return from.plusSeconds(3600);
        }
        return next.toInstant();
    }

    /** Convert 5-field unix cron to Spring 6-field (seconds + rest). */
    public static String toSpringCron(String fiveField) {
        String c = fiveField == null ? "" : fiveField.trim();
        String[] parts = c.split("\\s+");
        if (parts.length == 6) {
            return c;
        }
        if (parts.length == 5) {
            return "0 " + c;
        }
        return c;
    }

    private Map<String, Object> mutateSuite(
            Path projectRoot,
            String suiteId,
            SuiteMutator mutator
    ) throws IOException {
        validateId(suiteId, "suiteId");
        List<Map<String, Object>> all = new ArrayList<>(ensureSeeded(projectRoot));
        boolean found = false;
        Map<String, Object> updated = null;
        for (int i = 0; i < all.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>(all.get(i));
            if (!suiteId.trim().equals(str(row.get("suiteId")))) {
                continue;
            }
            mutator.apply(row);
            row.put("updatedAt", Instant.now().toString());
            all.set(i, row);
            updated = normalize(row);
            found = true;
            break;
        }
        if (!found) {
            throw new NotFoundException("Suite not found: " + suiteId);
        }
        writeAll(projectRoot, all);
        return updated;
    }

    private List<Map<String, Object>> ensureSeeded(Path projectRoot) throws IOException {
        List<Map<String, Object>> all = readAll(projectRoot);
        boolean changed = false;
        Set<String> haveBuiltin = new LinkedHashSet<>();
        for (Map<String, Object> row : all) {
            if (bool(row.get("builtIn"))) {
                for (String t : tagsOf(row)) {
                    haveBuiltin.add(t.toLowerCase(Locale.ROOT));
                }
            }
        }
        String now = Instant.now().toString();
        for (String tag : List.of("smoke", "regression", "nightly")) {
            if (haveBuiltin.contains(tag)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("suiteId", UUID.randomUUID().toString());
            row.put("name", Character.toUpperCase(tag.charAt(0)) + tag.substring(1));
            row.put("tags", List.of(tag));
            row.put("tcIds", List.of());
            row.put("builtIn", true);
            row.put("alertChannelId", null);
            row.put("alertMinSeverity", null);
            row.put("schedule", null);
            row.put("monitor", null);
            row.put("createdAt", now);
            row.put("updatedAt", now);
            all.add(row);
            changed = true;
        }
        if (changed || !Files.isRegularFile(file(projectRoot))) {
            writeAll(projectRoot, all);
            return readAll(projectRoot);
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readAll(Path projectRoot) throws IOException {
        Path path = file(projectRoot);
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        Object raw = mapper.readValue(path.toFile(), Object.class);
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                out.add(normalize((Map<String, Object>) map));
            }
        }
        return out;
    }

    private void writeAll(Path projectRoot, List<Map<String, Object>> rows) throws IOException {
        Path path = file(projectRoot);
        Files.createDirectories(path.getParent() == null ? projectRoot : path.getParent());
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            normalized.add(normalize(row));
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), normalized);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("suiteId", str(raw.get("suiteId")));
        m.put("name", str(raw.get("name")));
        m.put("tags", tagsOf(raw));
        m.put("tcIds", normalizeTcIds(asStringList(raw.get("tcIds"))));
        m.put("builtIn", bool(raw.get("builtIn")));
        Object alert = raw.get("alertChannelId");
        m.put("alertChannelId", alert == null || str(alert).isBlank() ? null : str(alert));
        Object sev = raw.get("alertMinSeverity");
        m.put("alertMinSeverity", sev == null || str(sev).isBlank() ? null : str(sev));
        Object schedule = raw.get("schedule");
        if (schedule instanceof Map<?, ?> sm) {
            m.put("schedule", normalizeSchedule((Map<String, Object>) sm));
        } else {
            m.put("schedule", null);
        }
        Object monitor = raw.get("monitor");
        if (monitor instanceof Map<?, ?> mm) {
            m.put("monitor", normalizeMonitor((Map<String, Object>) mm));
        } else {
            m.put("monitor", null);
        }
        m.put("createdAt", str(raw.get("createdAt")));
        m.put("updatedAt", str(raw.get("updatedAt")));
        return m;
    }

    static Map<String, Object> normalizeSchedule(Map<String, Object> raw) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("enabled", bool(raw.get("enabled")));
        String kind = str(raw.get("kind")).toLowerCase(Locale.ROOT);
        s.put("kind", kind.isBlank() ? "interval" : kind);
        Object cron = raw.get("cron");
        s.put("cron", cron == null || str(cron).isBlank() ? null : str(cron));
        Object im = raw.get("intervalMinutes");
        if (im == null || str(im).isBlank() || "null".equalsIgnoreCase(str(im))) {
            s.put("intervalMinutes", null);
        } else if (im instanceof Number n) {
            s.put("intervalMinutes", n.intValue());
        } else {
            try {
                s.put("intervalMinutes", Integer.parseInt(str(im)));
            } catch (Exception e) {
                s.put("intervalMinutes", null);
            }
        }
        Object next = raw.get("nextRunAt");
        s.put("nextRunAt", next == null || str(next).isBlank() ? null : str(next));
        Object last = raw.get("lastFiredAt");
        s.put("lastFiredAt", last == null || str(last).isBlank() ? null : str(last));
        Object job = raw.get("lastJobId");
        s.put("lastJobId", job == null || str(job).isBlank() ? null : str(job));
        return s;
    }

    static Map<String, Object> normalizeMonitor(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", bool(raw.get("enabled")));
        String target = str(raw.get("target")).toLowerCase(Locale.ROOT);
        m.put("target", target.isBlank() ? "prod" : target);
        m.put("promotedAt", str(raw.get("promotedAt")));
        Object from = raw.get("promotedFromJobId");
        m.put("promotedFromJobId", from == null || str(from).isBlank() ? null : str(from));
        Object status = raw.get("lastStatus");
        m.put("lastStatus", status == null || str(status).isBlank() ? "unknown" : str(status));
        Object checked = raw.get("lastCheckedAt");
        m.put("lastCheckedAt", checked == null || str(checked).isBlank() ? null : str(checked));
        return m;
    }

    static List<String> tagsOf(Map<String, Object> row) {
        return normalizeTags(asStringList(row.get("tags")), false);
    }

    static List<String> normalizeTags(List<String> tags, boolean requireNonBlank) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tags == null) {
            return List.of();
        }
        for (String t : tags) {
            if (t == null) {
                continue;
            }
            String v = t.trim();
            if (v.isEmpty()) {
                continue;
            }
            out.add(v);
        }
        return List.copyOf(out);
    }

    static List<String> normalizeTcIds(List<String> tcIds) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tcIds == null) {
            return List.of();
        }
        for (String id : tcIds) {
            if (id == null) {
                continue;
            }
            String v = id.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    static List<String> asStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return List.of(String.valueOf(raw));
    }

    static boolean sameTagSet(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        Set<String> left = new LinkedHashSet<>();
        for (String s : a) {
            left.add(s.toLowerCase(Locale.ROOT));
        }
        Set<String> right = new LinkedHashSet<>();
        for (String s : b) {
            right.add(s.toLowerCase(Locale.ROOT));
        }
        return left.equals(right);
    }

    public static void validateId(String id, String label) {
        if (id == null || id.isBlank() || !ID.matcher(id.trim()).matches()) {
            throw new BadPathException("Invalid " + label);
        }
        String lower = id.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\")) {
            throw new BadPathException("Invalid " + label);
        }
    }

    static boolean bool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o != null && Boolean.parseBoolean(str(o));
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @FunctionalInterface
    private interface SuiteMutator {
        void apply(Map<String, Object> row) throws IOException;
    }

    public static class BadPathException extends RuntimeException {
        public BadPathException(String message) {
            super(message);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }
}
