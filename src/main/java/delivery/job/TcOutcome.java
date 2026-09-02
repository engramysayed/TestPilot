package delivery.job;

import delivery.codegen.ProvenStep;

import java.nio.file.Path;
import java.util.List;

public record TcOutcome(
        String tcId,
        String title,
        TcStatus status,
        List<ProvenStep> provenSteps,
        String failureReason,
        Path evidenceDir,
        boolean needsLoginBeforeMethod,
        List<ProvenStep> loginSteps
) {
    public TcOutcome(
            String tcId,
            TcStatus status,
            List<ProvenStep> provenSteps,
            String failureReason,
            Path evidenceDir
    ) {
        this(tcId, "", status, provenSteps, failureReason, evidenceDir, false,
                provenSteps == null ? List.of() : List.of());
    }

    public TcOutcome(
            String tcId,
            TcStatus status,
            List<ProvenStep> provenSteps,
            String failureReason,
            Path evidenceDir,
            boolean needsLoginBeforeMethod,
            List<ProvenStep> loginSteps
    ) {
        this(tcId, "", status, provenSteps, failureReason, evidenceDir,
                needsLoginBeforeMethod, loginSteps);
    }

    public TcOutcome withLogin(boolean needsLogin, List<ProvenStep> loginPrelude) {
        return new TcOutcome(
                tcId,
                title,
                status,
                provenSteps,
                failureReason,
                evidenceDir,
                needsLogin,
                loginPrelude == null ? List.of() : List.copyOf(loginPrelude)
        );
    }

    public TcOutcome withTitle(String excelTitle) {
        return new TcOutcome(
                tcId,
                excelTitle == null ? "" : excelTitle,
                status,
                provenSteps,
                failureReason,
                evidenceDir,
                needsLoginBeforeMethod,
                loginSteps
        );
    }
}
