package delivery.agent.validation.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned aggregate of {@link ValidationFinding} for APIs, persistence, and harness context. */
public record ValidationReport(
        String contractId,
        String contractVersion,
        boolean passed,
        List<ValidationFinding> findings,
        Map<String, String> metadata) {

    public static final String VERSION = "1";

    public ValidationReport {
        contractId = contractId == null ? "" : contractId;
        contractVersion = contractVersion == null ? VERSION : contractVersion;
        findings = findings == null ? List.of() : List.copyOf(findings);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        passed = passed && findings.stream().noneMatch(ValidationFinding::blocksAcceptance);
    }

    public static ValidationReport pass(String contractId) {
        return new ValidationReport(contractId, VERSION, true, List.of(), Map.of());
    }

    public static ValidationReport of(String contractId, List<ValidationFinding> findings) {
        return of(contractId, VERSION, findings, Map.of());
    }

    public static ValidationReport of(
            String contractId,
            String contractVersion,
            List<ValidationFinding> findings,
            Map<String, String> metadata) {
        boolean ok = findings == null || findings.stream().noneMatch(ValidationFinding::blocksAcceptance);
        List<ValidationFinding> list = findings == null ? List.of() : findings;
        Map<String, String> meta = metadata == null ? Map.of() : Map.copyOf(metadata);
        String ver = contractVersion == null ? VERSION : contractVersion;
        return new ValidationReport(contractId, ver, ok, list, meta);
    }

    public List<String> mandatoryMessages() {
        List<String> out = new ArrayList<>();
        for (ValidationFinding f : findings) {
            if (f.severity() == ValidationSeverity.MANDATORY && !f.description().isBlank()) {
                out.add(f.description());
            }
        }
        return List.copyOf(out);
    }

    public Map<String, Object> toApiMap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contractId", contractId);
        body.put("contractVersion", contractVersion);
        body.put("passed", passed);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ValidationFinding f : findings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleId", f.ruleId());
            row.put("description", f.description());
            row.put("severity", f.severity().name());
            if (!f.affectedArtifact().isBlank()) {
                row.put("affectedArtifact", f.affectedArtifact());
            }
            if (!f.file().isBlank()) {
                row.put("file", f.file());
            }
            if (f.line() != null) {
                row.put("line", f.line());
            }
            if (!f.evidence().isBlank()) {
                row.put("evidence", f.evidence());
            }
            if (!f.suggestedCorrection().isBlank()) {
                row.put("suggestedCorrection", f.suggestedCorrection());
            }
            rows.add(row);
        }
        body.put("findings", rows);
        body.put("metadata", metadata);
        return body;
    }
}