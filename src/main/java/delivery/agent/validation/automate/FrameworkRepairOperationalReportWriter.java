package delivery.agent.validation.automate;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes {@code framework-acceptance/framework-repair-operational.json}. */
public final class FrameworkRepairOperationalReportWriter {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private FrameworkRepairOperationalReportWriter() {
    }

    public static Path reportPath(Path workDir) {
        return FrameworkAcceptanceWorkspace.acceptanceRoot(workDir).resolve("framework-repair-operational.json");
    }

    public static void write(
            Path workDir,
            FrameworkRepairOperationalTrace trace,
            AutomateFrameworkAcceptanceOutcome outcome) throws Exception {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", 1);
        doc.put("writtenAt", Instant.now().toString());
        doc.put("accepted", outcome != null && outcome.accepted());
        doc.put("gateStatus", outcome == null ? "" : outcome.status().name());
        if (trace != null) {
            doc.put("trace", trace);
        }
        Path dest = reportPath(workDir);
        Files.createDirectories(dest.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), doc);
    }
}