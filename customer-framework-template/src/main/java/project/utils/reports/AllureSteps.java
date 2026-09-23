package project.utils.reports;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import project.validations.Validation;

import java.util.UUID;

/**
 * Starts a named Allure step around the real action without putting typed values on the step.
 * Thrown actions fail the step. Soft-assertion recordings fail the step without throwing.
 */
public final class AllureSteps {
    private AllureSteps() {
    }

    public static void run(String name, Runnable action) {
        String uuid = UUID.randomUUID().toString();
        String label = name == null || name.isBlank() ? "step" : name;
        Allure.getLifecycle().startStep(uuid, new StepResult().setName(label));
        boolean finished = false;
        try {
            int failuresBefore = Validation.pendingFailureCount();
            action.run();
            Status status = Validation.pendingFailureCount() > failuresBefore ? Status.FAILED : Status.PASSED;
            Allure.getLifecycle().updateStep(uuid, result -> result.setStatus(status));
            finished = true;
        } catch (RuntimeException | Error e) {
            Allure.getLifecycle().updateStep(uuid, result -> result
                    .setStatus(Status.FAILED)
                    .setStatusDetails(new StatusDetails().setMessage(
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            finished = true;
            throw e;
        } finally {
            if (!finished) {
                Allure.getLifecycle().updateStep(uuid, result -> result.setStatus(Status.BROKEN));
            }
            Allure.getLifecycle().stopStep(uuid);
        }
    }
}
