package delivery.job;

import delivery.identity.PublicationLock;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tenant spend ledger. Reservations are atomic under {@link PublicationLock} so concurrent
 * jobs cannot consume the same remaining allowance.
 */
public final class BudgetLedger {
    public enum CostKind { ESTIMATED, UNKNOWN, RECONCILED }

    public record Limits(long hardCapUnits, long warningUnits) {
        public static Limits defaults() {
            return fromEnvironment();
        }

        public static Limits fromEnvironment() {
            long hard = longProp("delivery.budget.hard-cap-units", "DELIVERY_BUDGET_HARD_CAP_UNITS", 1_000_000L);
            long warn = longProp("delivery.budget.warning-units", "DELIVERY_BUDGET_WARNING_UNITS", 800_000L);
            return new Limits(Math.max(0, hard), Math.max(0, warn));
        }

        private static long longProp(String prop, String env, long fallback) {
            String raw = System.getProperty(prop, "");
            if (raw == null || raw.isBlank()) {
                raw = System.getenv().getOrDefault(env, "");
            }
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    public record Reservation(String jobId, long estimatedUnits, CostKind kind, boolean warning) {
    }

    public record UsageRecord(
            String jobId,
            long estimatedUnits,
            long reconciledUnits,
            CostKind estimatedKind,
            CostKind reconciledKind,
            String at
    ) {
    }

    public static final class Rejected extends RuntimeException {
        private final String code;

        public Rejected(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Path file;
    private final Limits limits;

    public BudgetLedger(Path file, Limits limits) {
        this.file = file;
        this.limits = limits == null ? Limits.defaults() : limits;
    }

    public Reservation reserve(String jobId, long estimatedUnits, CostKind kind) throws Exception {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        CostKind costKind = kind == null ? CostKind.ESTIMATED : kind;
        if (costKind == CostKind.UNKNOWN || estimatedUnits < 0) {
            throw new Rejected("COST_UNKNOWN", "unknown cost cannot be reserved");
        }
        if (limits.hardCapUnits() <= 0) {
            throw new Rejected("BUDGET_CLOSED", "budget hard cap is fail-closed");
        }
        AtomicBoolean warned = new AtomicBoolean(false);
        return PublicationLock.call(file.resolveSibling("budget.lock"), () -> {
            JSONObject root = read();
            long reserved = reservedTotal(root);
            if (root.has("reservations") && root.getJSONObject("reservations").has(jobId)) {
                JSONObject existing = root.getJSONObject("reservations").getJSONObject(jobId);
                return new Reservation(
                        jobId,
                        existing.optLong("estimatedUnits"),
                        CostKind.valueOf(existing.optString("kind", CostKind.ESTIMATED.name())),
                        false);
            }
            if (reserved + estimatedUnits > limits.hardCapUnits()) {
                throw new Rejected("BUDGET_EXCEEDED",
                        "tenant reserved " + reserved + " of " + limits.hardCapUnits());
            }
            JSONObject reservations = root.optJSONObject("reservations");
            if (reservations == null) {
                reservations = new JSONObject();
                root.put("reservations", reservations);
            }
            JSONObject row = new JSONObject();
            row.put("estimatedUnits", estimatedUnits);
            row.put("kind", costKind.name());
            row.put("at", Instant.now().toString());
            reservations.put(jobId, row);
            boolean warning = reserved + estimatedUnits >= limits.warningUnits() && limits.warningUnits() > 0;
            warned.set(warning);
            appendUsage(root, new UsageRecord(
                    jobId, estimatedUnits, 0, costKind, CostKind.ESTIMATED, Instant.now().toString()));
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
            return new Reservation(jobId, estimatedUnits, costKind, warning);
        });
    }

    public UsageRecord reconcile(String jobId, long reconciledUnits) throws Exception {
        return PublicationLock.call(file.resolveSibling("budget.lock"), () -> {
            JSONObject root = read();
            JSONObject reservations = root.optJSONObject("reservations");
            long estimated = 0;
            CostKind estimatedKind = CostKind.ESTIMATED;
            if (reservations != null && reservations.has(jobId)) {
                JSONObject row = reservations.getJSONObject(jobId);
                estimated = row.optLong("estimatedUnits");
                estimatedKind = CostKind.valueOf(row.optString("kind", CostKind.ESTIMATED.name()));
                reservations.remove(jobId);
            }
            UsageRecord usage = new UsageRecord(
                    jobId, estimated, Math.max(0, reconciledUnits), estimatedKind, CostKind.RECONCILED,
                    Instant.now().toString());
            appendUsage(root, usage);
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
            return usage;
        });
    }

    public List<UsageRecord> history() throws Exception {
        JSONObject root = read();
        JSONArray usage = root.optJSONArray("usage");
        List<UsageRecord> out = new ArrayList<>();
        if (usage == null) {
            return List.of();
        }
        for (int i = 0; i < usage.length(); i++) {
            JSONObject row = usage.getJSONObject(i);
            out.add(new UsageRecord(
                    row.optString("jobId"),
                    row.optLong("estimatedUnits"),
                    row.optLong("reconciledUnits"),
                    CostKind.valueOf(row.optString("estimatedKind", CostKind.ESTIMATED.name())),
                    CostKind.valueOf(row.optString("reconciledKind", CostKind.ESTIMATED.name())),
                    row.optString("at")));
        }
        return List.copyOf(out);
    }

    private JSONObject read() throws Exception {
        if (!Files.isRegularFile(file)) {
            JSONObject empty = new JSONObject();
            empty.put("reservations", new JSONObject());
            empty.put("usage", new JSONArray());
            return empty;
        }
        return new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static long reservedTotal(JSONObject root) {
        JSONObject reservations = root.optJSONObject("reservations");
        if (reservations == null) {
            return 0;
        }
        long total = 0;
        for (String key : reservations.keySet()) {
            total += reservations.getJSONObject(key).optLong("estimatedUnits");
        }
        return total;
    }

    private static void appendUsage(JSONObject root, UsageRecord usage) {
        JSONArray arr = root.optJSONArray("usage");
        if (arr == null) {
            arr = new JSONArray();
            root.put("usage", arr);
        }
        JSONObject row = new JSONObject();
        row.put("jobId", usage.jobId());
        row.put("estimatedUnits", usage.estimatedUnits());
        row.put("reconciledUnits", usage.reconciledUnits());
        row.put("estimatedKind", usage.estimatedKind().name());
        row.put("reconciledKind", usage.reconciledKind().name());
        row.put("at", usage.at());
        arr.put(row);
    }
}
