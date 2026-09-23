package project.utils.Actions;

/** Failed replay action. Must fail the test rather than continue to later steps. */
public class ReplayActionException extends RuntimeException {
    public ReplayActionException(String message) {
        super(message);
    }

    public ReplayActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
