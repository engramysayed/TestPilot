package delivery.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-local approved run-memory hints for heal prompts (A4). */
public final class RunMemoryHintsStore {
    public static final String JOB_FILE = "run-memory-approved.json";

    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunMemoryHintsStore() {
    }

    public static List<String> loadForJob(Path projectRoot, String jobId) {
        if (projectRoot == null || jobId == null || jobId.isBlank()) {
            return List.of();
        }
        Path file = projectRoot.resolve("execute-runs").resolve(jobId.trim()).resolve(JOB_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = MAPPER.readValue(file.toFile(), Map.class);
            Object items = doc.get("items");
            if (!(items instanceof List<?> list)) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                String id = str(m.get("id"));
                String hint = str(m.get("locatorHint"));
                String summary = str(m.get("summary"));
                if (hint.isBlank() && summary.isBlank()) {
                    continue;
                }
                if (!id.isBlank()) {
                    ids.add(id);
                }
                String line = hint.isBlank() ? summary : (summary.isBlank() ? hint : summary + " → " + hint);
                lines.add(line.length() > 300 ? line.substring(0, 300) : line);
            }
            return List.copyOf(lines);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static Scope activate(Path projectRoot, String jobId) {
        List<String> lines = loadForJob(projectRoot, jobId);
        List<String> ids = loadIds(projectRoot, jobId);
        Context ctx = new Context(lines, ids, projectRoot, new AtomicBoolean(false));
        Context previous = ACTIVE.get();
        ACTIVE.set(ctx);
        return new Scope(previous);
    }

    @SuppressWarnings("unchecked")
    private static List<String> loadIds(Path projectRoot, String jobId) {
        Path file = projectRoot.resolve("execute-runs").resolve(jobId.trim()).resolve(JOB_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            Map<String, Object> doc = MAPPER.readValue(file.toFile(), Map.class);
            Object items = doc.get("items");
            if (!(items instanceof List<?> list)) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String id = str(m.get("id"));
                    if (!id.isBlank()) {
                        ids.add(id);
                    }
                }
            }
            return List.copyOf(ids);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<String> currentLines() {
        Context c = ACTIVE.get();
        return c == null ? List.of() : c.lines;
    }

    /** Called when heal payload includes run-memory hints (once per job). */
    public static void markUsedOnHeal() {
        Context c = ACTIVE.get();
        if (c == null || c.lines.isEmpty() || !c.used.compareAndSet(false, true)) {
            return;
        }
        RunMemoryUsedRecorder.recordUse(c.projectRoot, c.memIds);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private record Context(
            List<String> lines,
            List<String> memIds,
            Path projectRoot,
            AtomicBoolean used
    ) {
    }

    public static Scope noop() {
        return new Scope(null, true);
    }

    public static final class Scope implements AutoCloseable {
        private final Context previous;
        private final boolean noop;

        private Scope(Context previous) {
            this(previous, false);
        }

        private Scope(Context previous, boolean noop) {
            this.previous = previous;
            this.noop = noop;
        }

        @Override
        public void close() {
            if (noop) {
                return;
            }
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }
}
