package delivery.job;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Cooperative job cancellation — checked between TC/story iterations and while
 * waiting on a blocking Ollama call.
 */
public final class JobCancelSupport {

    private static final long POLL_MS = 250L;

    private JobCancelSupport() {
    }

    public static void checkCancelled(BooleanSupplier cancelCheck) {
        if (cancelCheck != null && cancelCheck.getAsBoolean()) {
            throw new JobCancelledException();
        }
    }

    /**
     * Wait for {@code future} up to {@code timeoutMs}, aborting as soon as cancel is requested.
     */
    public static <T> T awaitOrCancel(Future<T> future, long timeoutMs, BooleanSupplier cancelCheck)
            throws Exception {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (true) {
            checkCancelled(cancelCheck);
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                future.cancel(true);
                throw new TimeoutException();
            }
            try {
                return future.get(Math.min(POLL_MS, remaining), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // poll cancel flag again
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                checkCancelled(cancelCheck);
                throw e;
            } catch (java.util.concurrent.ExecutionException e) {
                if (cancelCheck != null && cancelCheck.getAsBoolean()) {
                    throw new JobCancelledException();
                }
                throw e;
            }
        }
    }
}
