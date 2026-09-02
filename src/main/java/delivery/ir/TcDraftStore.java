package delivery.ir;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Read/write Phase-1 IR drafts under {@code work/ir/}. */
public class TcDraftStore {
    private final Path irDir;

    public TcDraftStore(Path workDir) {
        this.irDir = workDir.resolve("ir");
    }

    public Path irDir() {
        return irDir;
    }

    public void write(TcDraft draft) throws Exception {
        Files.createDirectories(irDir);
        Path file = irDir.resolve(safeFileName(draft.tcId()) + ".json");
        Files.writeString(file, toJson(draft).toString(2), StandardCharsets.UTF_8);
    }

    public TcDraft read(String tcId) throws Exception {
        Path file = irDir.resolve(safeFileName(tcId) + ".json");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("IR draft not found: " + tcId);
        }
        return fromJson(new JSONObject(Files.readString(file, StandardCharsets.UTF_8)));
    }

    public List<TcDraft> readAll() throws Exception {
        if (!Files.isDirectory(irDir)) {
            return List.of();
        }
        List<TcDraft> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(irDir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            out.add(fromJson(new JSONObject(Files.readString(p, StandardCharsets.UTF_8))));
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to read IR draft: " + p, e);
                        }
                    });
        }
        return out;
    }

    public static JSONObject toJson(TcDraft d) {
        JSONObject o = new JSONObject();
        o.put("tcId", d.tcId());
        o.put("title", d.title());
        o.put("stepsText", d.stepsText());
        o.put("expectedResult", d.expectedResult());
        o.put("status", d.status().name());
        o.put("provenSteps", ProvenStepCodec.toJsonArray(d.provenSteps()));
        o.put("loginSteps", ProvenStepCodec.toJsonArray(d.loginSteps()));
        o.put("needsLoginBeforeMethod", d.needsLoginBeforeMethod());
        o.put("blockerStepIndex", d.blockerStepIndex());
        o.put("blockerIntent", d.blockerIntent());
        o.put("failureReason", d.failureReason());
        o.put("evidenceDir", d.evidenceDir());
        o.put("retryCountOnBlocker", d.retryCountOnBlocker());
        o.put("lastPageUrl", d.lastPageUrl());
        o.put("healTier", d.healTier());
        o.put("healSkipReason", d.healSkipReason());
        o.put("loginFormUrl", d.loginFormUrl());
        return o;
    }

    public static TcDraft fromJson(JSONObject o) {
        return new TcDraft(
                o.optString("tcId", ""),
                o.optString("title", ""),
                o.optString("stepsText", ""),
                o.optString("expectedResult", ""),
                TcDraftStatus.valueOf(o.optString("status", "TODO")),
                ProvenStepCodec.fromJsonArray(o.optJSONArray("provenSteps")),
                ProvenStepCodec.fromJsonArray(o.optJSONArray("loginSteps")),
                o.optBoolean("needsLoginBeforeMethod", false),
                o.optInt("blockerStepIndex", -1),
                o.optString("blockerIntent", ""),
                o.optString("failureReason", ""),
                o.optString("evidenceDir", ""),
                o.optInt("retryCountOnBlocker", 0),
                o.optString("lastPageUrl", ""),
                o.optString("healTier", "none"),
                o.optString("healSkipReason", ""),
                o.optString("loginFormUrl", "")
        );
    }

    public static String safeFileName(String tcId) {
        if (tcId == null || tcId.isBlank()) {
            return "tc";
        }
        return tcId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
