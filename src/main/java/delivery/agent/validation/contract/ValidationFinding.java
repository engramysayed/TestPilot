package delivery.agent.validation.contract;

/**
 * One validation outcome. User-defined requirements are never invented — ambiguous rules use
 * {@link #ruleId()} {@code UNDEFINED_RULE} and request clarification.
 */
public record ValidationFinding(
        String ruleId,
        String description,
        ValidationSeverity severity,
        String affectedArtifact,
        String file,
        Integer line,
        String evidence,
        String suggestedCorrection) {

    public static final String UNDEFINED_RULE = "UNDEFINED_RULE";

    public ValidationFinding {
        ruleId = ruleId == null || ruleId.isBlank() ? "RULE" : ruleId.trim();
        description = description == null ? "" : description;
        severity = severity == null ? ValidationSeverity.MANDATORY : severity;
        affectedArtifact = affectedArtifact == null ? "" : affectedArtifact;
        file = file == null ? "" : file;
        evidence = evidence == null ? "" : evidence;
        suggestedCorrection = suggestedCorrection == null ? "" : suggestedCorrection;
    }

    public boolean blocksAcceptance() {
        return severity == ValidationSeverity.MANDATORY;
    }
}