package delivery.job;

/**
 * Thrown when a cooperative cancel is detected between TC/story iterations.
 */
public class JobCancelledException extends RuntimeException {
    public JobCancelledException() {
        super("Job cancelled");
    }
}
