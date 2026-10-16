package delivery.authoring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-prove-run bind quality counters. Thread-confined — one instance on ProvePhase.
 */
public final class BindQualityMetrics {
    public int bindClear;
    public int bindTie;
    public int bindEmpty;
    public int catalogHit;
    public int catalogRebuild;
    public int ollamaAsked;
    public int visionAsked;
    public int healEntered;
    public int falseBind;

    public void add(BindDecision.Kind kind) {
        if (kind == null) {
            return;
        }
        switch (kind) {
            case CLEAR -> bindClear++;
            case TIE -> bindTie++;
            case EMPTY -> bindEmpty++;
        }
    }

    public String summaryLine() {
        return "BIND_QUALITY clear=" + bindClear
                + " tie=" + bindTie
                + " empty=" + bindEmpty
                + " catalogHit=" + catalogHit
                + " catalogRebuild=" + catalogRebuild
                + " ollama=" + ollamaAsked
                + " vision=" + visionAsked
                + " heal=" + healEntered;
    }

    public void writeJson(Path file) throws IOException {
        if (file == null) {
            return;
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = "{"
                + "\"clear\":" + bindClear + ","
                + "\"tie\":" + bindTie + ","
                + "\"empty\":" + bindEmpty + ","
                + "\"catalogHit\":" + catalogHit + ","
                + "\"catalogRebuild\":" + catalogRebuild + ","
                + "\"ollama\":" + ollamaAsked + ","
                + "\"vision\":" + visionAsked + ","
                + "\"heal\":" + healEntered
                + "}";
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }
}