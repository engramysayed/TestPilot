package delivery.agent.validation.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Persists versioned contract reports under {@code agent-runs/{parentJobId}/}. */
public final class ContractValidationReportWriter {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ContractValidationReportWriter() {
    }

    public static Path writeContractReport(Path projectRoot, String parentJobId, ValidationReport report)
            throws Exception {
        Path dir = projectRoot.resolve("agent-runs").resolve(sanitize(parentJobId));
        Files.createDirectories(dir);
        Path out = dir.resolve("contract-validation-report.json");
        JSON.writeValue(out.toFile(), report.toApiMap());
        return out;
    }

    public static String serialize(ValidationReport report) throws Exception {
        return JSON.writeValueAsString(report == null ? Map.of() : report.toApiMap());
    }

    private static String sanitize(String id) {
        if (id == null || id.isBlank()) {
            return "generate";
        }
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}