package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolve emitted test class file names (merge same page stem into one class). */
public final class CodegenTestClasses {
    private CodegenTestClasses() {
    }

    public static Map<String, String> fileNameByTcId(List<TcOutcome> outcomes) {
        Map<String, String> byTcId = new LinkedHashMap<>();
        if (outcomes == null) {
            return byTcId;
        }
        for (TcOutcome outcome : outcomes) {
            boolean passed = outcome.status() == TcStatus.PASSED;
            String className = classNameFor(outcome, passed);
            byTcId.put(outcome.tcId(), className + ".java");
        }
        return byTcId;
    }

    public static String classNameFor(TcOutcome outcome, boolean passed) {
        String primaryPage = PageUnderTest.primaryPageName(outcome);
        return CodegenNaming.testClassName(outcome.tcId(), primaryPage, passed);
    }

    public static String groupKey(TcOutcome outcome, boolean passed) {
        return (passed ? "P:" : "T:") + classNameFor(outcome, passed).toLowerCase(Locale.ROOT);
    }
}
