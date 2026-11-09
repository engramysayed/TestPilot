package delivery.job;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists a {@link SuiteRunContext} next to the job's {@code request.json} ({@code execute-runs/{jobId}/suite-context.json}).
 * <p>
 * The context is a ThreadLocal set by whoever starts a suite run, but the portal runs Execute jobs on an async worker
 * thread, so the run never saw it: no suite alerts, no monitor target URL. The worker now reads this file and activates
 * the context on its own thread.
 */
public final class SuiteRunContextFiles {
    public static final String FILE = "suite-context.json";

    private SuiteRunContextFiles() {
    }

    public static Path path(Path executeRunDir) {
        return executeRunDir.resolve(FILE);
    }

    public static void write(Path executeRunDir, SuiteRunContext ctx) throws java.io.IOException {
        if (ctx == null) {
            return;
        }
        Files.createDirectories(executeRunDir);
        JSONObject o = new JSONObject()
                .put("projectId", nz(ctx.projectId()))
                .put("suiteId", nz(ctx.suiteId()))
                .put("tag", nz(ctx.tag()))
                .put("tcIds", new JSONArray(ctx.tcIds()))
                .put("trigger", nz(ctx.trigger()))
                .put("alertChannelId", nz(ctx.alertChannelId()))
                .put("alertMinSeverity", nz(ctx.alertMinSeverity()))
                .put("monitorTarget", nz(ctx.monitorTarget()));
        Files.writeString(path(executeRunDir), o.toString(2), StandardCharsets.UTF_8);
    }

    public static Optional<SuiteRunContext> read(Path executeRunDir) {
        try {
            Path p = path(executeRunDir);
            if (!Files.isRegularFile(p)) {
                return Optional.empty();
            }
            JSONObject o = new JSONObject(Files.readString(p, StandardCharsets.UTF_8));
            List<String> tcIds = new ArrayList<>();
            JSONArray arr = o.optJSONArray("tcIds");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    tcIds.add(arr.optString(i, ""));
                }
            }
            return Optional.of(new SuiteRunContext(
                    o.optString("projectId", ""),
                    o.optString("suiteId", ""),
                    o.optString("tag", ""),
                    tcIds,
                    o.optString("trigger", ""),
                    o.optString("alertChannelId", ""),
                    o.optString("alertMinSeverity", ""),
                    o.optString("monitorTarget", "")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
