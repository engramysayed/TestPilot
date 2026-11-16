package delivery.agent.validation.automate;

import delivery.agent.validation.contract.ValidationReport;

/** Result of the Automate acceptance gate (deterministic validation ± optional repair). */
public record AutomateFrameworkAcceptanceOutcome(
        AutomateFrameworkAcceptanceStatus status,
        ValidationReport validationReport,
        ValidationReport revalidationReport,
        String taskId,
        String detail,
        boolean accepted) {

    public static AutomateFrameworkAcceptanceOutcome accepted(ValidationReport report) {
        return new AutomateFrameworkAcceptanceOutcome(
                AutomateFrameworkAcceptanceStatus.ACCEPTED,
                report,
                report,
                "",
                "contract and compile passed",
                true);
    }

    public static AutomateFrameworkAcceptanceOutcome contractPassed(ValidationReport report) {
        return new AutomateFrameworkAcceptanceOutcome(
                AutomateFrameworkAcceptanceStatus.CONTRACT_PASSED,
                report,
                report,
                "",
                "",
                true);
    }

    public static AutomateFrameworkAcceptanceOutcome rejected(
            AutomateFrameworkAcceptanceStatus status,
            ValidationReport report,
            ValidationReport revalidate,
            String taskId,
            String detail) {
        return new AutomateFrameworkAcceptanceOutcome(
                status,
                report,
                revalidate == null ? report : revalidate,
                taskId == null ? "" : taskId,
                detail == null ? "" : detail,
                false);
    }
}