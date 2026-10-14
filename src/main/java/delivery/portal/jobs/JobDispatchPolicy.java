package delivery.portal.jobs;

import delivery.portal.model.JobRecord;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * B08: where each job kind runs. {@code keel.jobs.dispatch.<kind>=inprocess|remote} (kind = execute, convert,
 * generate-batch, generate-compare, hunt), falling back to {@code keel.jobs.dispatch.default} (default
 * {@code inprocess}). {@code remote} jobs are flagged for the engine (Runner API v2 on 8080) and in-process
 * workers skip them; {@code inprocess} is the migration fallback and goes away in 0b. Fail-closed: asking for
 * {@code remote} on a kind the 0a engine can't run refuses startup.
 */
@Component
public class JobDispatchPolicy {
    public enum Mode { INPROCESS, REMOTE }

    /** Kinds keel-engine's EngineAgent claims in 0a (keep in sync with delivery.runner.EngineAgent.REMOTE_KINDS). */
    public static final Set<JobRecord.JobKind> REMOTE_CAPABLE = EnumSet.of(JobRecord.JobKind.EXECUTE, JobRecord.JobKind.CONVERT);

    private final Map<JobRecord.JobKind, Mode> modes = new EnumMap<>(JobRecord.JobKind.class);

    @org.springframework.beans.factory.annotation.Autowired
    public JobDispatchPolicy(Environment env) {
        this(key -> env.getProperty(key));
    }

    public JobDispatchPolicy(java.util.function.Function<String, String> props) {
        Mode fallback = parse("keel.jobs.dispatch.default", props.apply("keel.jobs.dispatch.default"), Mode.INPROCESS);
        StringBuilder refused = new StringBuilder();
        for (JobRecord.JobKind kind : JobRecord.JobKind.values()) {
            String key = "keel.jobs.dispatch." + key(kind);
            String raw = props.apply(key);
            Mode m = parse(key, raw, fallback);
            if (m == Mode.REMOTE && !REMOTE_CAPABLE.contains(kind)) {
                if (raw != null && !raw.isBlank()) {
                    refused.append(key).append("=remote ");
                }
                m = Mode.INPROCESS; // default=remote only applies to kinds the engine can run
            }
            modes.put(kind, m);
        }
        if (refused.length() > 0) {
            throw new IllegalStateException("DISPATCH_UNSUPPORTED: the 0a engine cannot run " + refused.toString().trim());
        }
    }

    public static String key(JobRecord.JobKind kind) {
        return kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Mode parse(String key, String raw, Mode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "inprocess", "in-process" -> Mode.INPROCESS;
            case "remote" -> Mode.REMOTE;
            default -> throw new IllegalStateException("DISPATCH_INVALID: " + key + "=" + raw + " (inprocess|remote)");
        };
    }

    public Mode mode(JobRecord.JobKind kind) {
        return kind == null ? Mode.INPROCESS : modes.get(kind);
    }

    public boolean remote(JobRecord.JobKind kind) {
        return mode(kind) == Mode.REMOTE;
    }
}
