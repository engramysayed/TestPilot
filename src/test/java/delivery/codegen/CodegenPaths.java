package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;

import java.util.List;
import java.util.Map;

/** Expected generated paths from page-under-test naming. */
public final class CodegenPaths {
    private CodegenPaths() {
    }

    public static String testClassFile(TcOutcome outcome, boolean passed) {
        return CodegenNaming.testClassName(
                outcome.tcId(), PageUnderTest.primaryPageName(outcome), passed) + ".java";
    }

    /** Same collision rules as {@link delivery.codegen.CodeWriter} when emitting a suite. */
    public static Map<String, String> testClassFilesInEmitOrder(List<TcOutcome> outcomes) {
        return CodegenTestClasses.fileNameByTcId(outcomes);
    }

    public static String pageFile(String pageName) {
        return CodegenNaming.pageClassName(pageName) + ".java";
    }
}
