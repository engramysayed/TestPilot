package delivery.job.port;

import delivery.excel.ManualTestCase;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Generate-batch worker -> case generator, bound to one project by the caller (B05). */
@FunctionalInterface
public interface StoryCaseGenerator {
    List<ManualTestCase> generate(String stories, boolean reviewPass, String storyLabel, String model,
                                  BooleanSupplier cancelCheck) throws Exception;
}
