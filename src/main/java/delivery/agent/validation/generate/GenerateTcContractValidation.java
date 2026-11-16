package delivery.agent.validation.generate;

import delivery.agent.validation.contract.TestCaseContractValidator;
import delivery.agent.validation.contract.TestCaseValidationContext;
import delivery.agent.validation.contract.ValidationReport;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.List;

/** Generate-facing façade over shared TC contract validation. */
public final class GenerateTcContractValidation {
    private GenerateTcContractValidation() {
    }

    public static ValidationReport validate(List<ManualTestCase> cases, TestCaseValidationContext context) {
        return TestCaseContractValidator.validate(cases, context == null ? TestCaseValidationContext.empty() : context);
    }

    /** Merge mandatory contract messages with legacy quality gate string errors for existing APIs. */
    public static List<String> toLegacyGateErrors(ValidationReport report) {
        if (report == null || report.passed()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(report.mandatoryMessages());
        return List.copyOf(out);
    }
}