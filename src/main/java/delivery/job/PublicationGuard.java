package delivery.job;

import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/** Executes authoritative publication only while the original attempt remains entitled to it. */
public interface PublicationGuard {
    <T> T publish(Callable<T> action) throws Exception;

    static PublicationGuard cancellable(BooleanSupplier cancel) {
        return new PublicationGuard() {
            public <T> T publish(Callable<T> action) throws Exception {
                JobCancelSupport.checkCancelled(cancel);
                return action.call();
            }
        };
    }
}
