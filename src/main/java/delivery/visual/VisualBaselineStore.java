package delivery.visual;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Disk SoT for visual baselines under the project root.
 * <p>
 * <b>Approve / rewrite is Backend+FE only</b> — AE Execute must not call
 * {@link #writeBaseline} on a green run (no silent seed).
 */
public final class VisualBaselineStore {
    private VisualBaselineStore() {
    }

    public static Optional<Path> findBaselinePng(Path projectRoot, String caseKey, String viewport) {
        if (projectRoot == null || caseKey == null || caseKey.isBlank()) {
            return Optional.empty();
        }
        Path png = VisualBaselinePaths.baselinePng(projectRoot, caseKey, viewport);
        return Files.isRegularFile(png) ? Optional.of(png) : Optional.empty();
    }

    public static Optional<VisualBaselineMeta> readMeta(Path projectRoot, String caseKey, String viewport) {
        Path meta = VisualBaselinePaths.baselineMeta(projectRoot, caseKey, viewport);
        if (!Files.isRegularFile(meta)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(VisualBaselineMeta.fromJson(
                    Files.readString(meta, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Explicit approve/seed path for Backend HTTP. Do not call from ProvePhase.
     */
    public static VisualBaselineMeta writeBaseline(
            Path projectRoot,
            String projectId,
            String caseKey,
            String viewport,
            byte[] png,
            String sourceRunId,
            int revision
    ) throws IOException {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot required");
        }
        if (caseKey == null || caseKey.isBlank()) {
            throw new IllegalArgumentException("caseKey required");
        }
        if (png == null || png.length == 0) {
            throw new IllegalArgumentException("baseline png empty");
        }
        String vp = VisualBaselinePaths.sanitizeViewport(viewport);
        Path dir = VisualBaselinePaths.baselineDir(projectRoot, caseKey, vp);
        Files.createDirectories(dir);
        Path out = dir.resolve(VisualBaselinePaths.BASELINE_PNG);
        Files.write(out, png);
        int[] wh = pngSize(png);
        VisualBaselineMeta meta = VisualBaselineMeta.of(
                projectId, caseKey, vp, revision, sourceRunId, wh[0], wh[1]);
        Files.writeString(dir.resolve(VisualBaselinePaths.META_JSON),
                meta.toJson().toString(2), StandardCharsets.UTF_8);
        return meta;
    }

    static int[] pngSize(byte[] png) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                return new int[]{0, 0};
            }
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (IOException e) {
            return new int[]{0, 0};
        }
    }
}
