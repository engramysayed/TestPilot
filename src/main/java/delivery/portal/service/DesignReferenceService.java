package delivery.portal.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DesignReferenceService {

    private static final Pattern TC_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
    private static final Pattern STEP_FILE = Pattern.compile("^step-(\\d{3})\\.png$");
    /** Highest step index a user may attach a Figma to. Keeps the file name 3-digit and the UI finite. */
    public static final int MAX_STEP_INDEX = 999;

    public Path referencesDir(Path projectRoot) {
        return projectRoot.resolve("design-references");
    }

    public Path referenceFile(Path projectRoot, String tcId) {
        validateTcId(tcId);
        return referencesDir(projectRoot).resolve(tcId + ".png");
    }

    public Path stepReferenceDir(Path projectRoot, String tcId) {
        validateTcId(tcId);
        return referencesDir(projectRoot).resolve(tcId);
    }

    public Path stepReferenceFile(Path projectRoot, String tcId, int stepIndex) {
        validateStepIndex(stepIndex);
        return stepReferenceDir(projectRoot, tcId).resolve(stepFileName(stepIndex));
    }

    public List<String> list(Path projectRoot) throws IOException {
        Path dir = referencesDir(projectRoot);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        TreeSet<String> all = new TreeSet<>();
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && name.endsWith(".png")) {
                    all.add(name.substring(0, name.length() - 4));
                } else if (Files.isDirectory(p) && TC_ID.matcher(name).matches()) {
                    all.add(name);
                }
            });
        }
        return List.copyOf(all);
    }

    /** Step indices the user has uploaded for one TC, ascending. Empty when no step directory exists. */
    public List<Integer> listSteps(Path projectRoot, String tcId) throws IOException {
        Path dir = stepReferenceDir(projectRoot, tcId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .forEach(name -> {
                        Matcher m = STEP_FILE.matcher(name);
                        if (m.matches()) {
                            out.add(Integer.parseInt(m.group(1)));
                        }
                    });
        }
        out.sort(Comparator.naturalOrder());
        return List.copyOf(out);
    }

    public Optional<Path> resolve(Path projectRoot, String tcId) {
        validateTcId(tcId);
        Path file = referenceFile(projectRoot, tcId);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    public Optional<Path> resolveStep(Path projectRoot, String tcId, int stepIndex) {
        Path file = stepReferenceFile(projectRoot, tcId, stepIndex);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    public void upload(Path projectRoot, String tcId, byte[] bytes, String contentType) throws IOException {
        validateTcId(tcId);
        if (bytes == null || bytes.length == 0) {
            throw new BadUploadException("File is empty");
        }
        byte[] png = toPng(bytes, contentType);
        Path dir = referencesDir(projectRoot);
        Files.createDirectories(dir);
        Files.write(dir.resolve(tcId + ".png"), png);
    }

    public void uploadStep(Path projectRoot, String tcId, int stepIndex, byte[] bytes, String contentType) throws IOException {
        validateTcId(tcId);
        validateStepIndex(stepIndex);
        if (bytes == null || bytes.length == 0) {
            throw new BadUploadException("File is empty");
        }
        byte[] png = toPng(bytes, contentType);
        Path dir = stepReferenceDir(projectRoot, tcId);
        Files.createDirectories(dir);
        Files.write(dir.resolve(stepFileName(stepIndex)), png);
    }

    private static String stepFileName(int stepIndex) {
        return String.format(Locale.ROOT, "step-%03d.png", stepIndex);
    }

    static void validateTcId(String tcId) {
        if (tcId == null || tcId.isBlank() || !TC_ID.matcher(tcId.trim()).matches()) {
            throw new BadPathException("Invalid tcId");
        }
    }

    static void validateStepIndex(int stepIndex) {
        if (stepIndex < 1 || stepIndex > MAX_STEP_INDEX) {
            throw new BadPathException("Invalid stepIndex (must be 1.." + MAX_STEP_INDEX + ")");
        }
    }

    static byte[] toPng(byte[] bytes, String contentType) throws IOException {
        String kind = detectKind(bytes, contentType);
        if ("png".equals(kind)) {
            return bytes;
        }
        if ("jpeg".equals(kind) || "webp".equals(kind)) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BadUploadException("Unsupported or corrupt image");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new BadUploadException("Failed to convert image to PNG");
            }
            return out.toByteArray();
        }
        throw new BadUploadException("Only PNG, JPG, and WEBP images are allowed");
    }

    static String detectKind(byte[] bytes, String contentType) {
        if (bytes != null && bytes.length >= 8) {
            if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
                return "png";
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
                return "jpeg";
            }
            if (bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
                return "webp";
            }
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("png")) {
                return "png";
            }
            if (ct.contains("jpeg") || ct.contains("jpg")) {
                return "jpeg";
            }
            if (ct.contains("webp")) {
                return "webp";
            }
        }
        return "unknown";
    }

    public static class BadPathException extends RuntimeException {
        public BadPathException(String message) {
            super(message);
        }
    }

    public static class BadUploadException extends RuntimeException {
        public BadUploadException(String message) {
            super(message);
        }
    }

    /** Pick the highest-numbered {@code step-NNN.png} in an evidence directory. */
    public static Optional<Path> lastStepScreenshot(Path evidenceDir) throws IOException {
        if (!Files.isDirectory(evidenceDir)) {
            return Optional.empty();
        }
        try (var stream = Files.list(evidenceDir)) {
            return stream.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.matches("step-\\d{3}\\.png"))
                    .max(Comparator.naturalOrder())
                    .map(name -> evidenceDir.resolve(name));
        }
    }

    /** Prefer visual-assert, then failure, then last step screenshot. */
    public static Optional<Path> pickActualScreenshot(Path evidenceDir) throws IOException {
        if (evidenceDir == null) {
            return Optional.empty();
        }
        Path visual = evidenceDir.resolve("visual-assert.png");
        if (Files.isRegularFile(visual)) {
            return Optional.of(visual);
        }
        Path failure = evidenceDir.resolve("failure.png");
        if (Files.isRegularFile(failure)) {
            return Optional.of(failure);
        }
        return lastStepScreenshot(evidenceDir);
    }
}
