package delivery.agent.validation.automate;

/** Reconciliation from agent workspace to repair-candidate failed — fail closed; do not compile or promote. */
public final class FrameworkRepairReconciliationException extends Exception {
    public FrameworkRepairReconciliationException(String message) {
        super(message);
    }

    public FrameworkRepairReconciliationException(String message, Throwable cause) {
        super(message, cause);
    }
}