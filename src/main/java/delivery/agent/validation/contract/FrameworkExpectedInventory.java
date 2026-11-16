package delivery.agent.validation.contract;

import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenTestClasses;
import delivery.codegen.PageAccumulator;
import delivery.codegen.CodegenNaming;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Expected page/test inventory from emit-time outcomes — not from workspace filenames. */
public record FrameworkExpectedInventory(
        List<ExpectedPage> pages,
        List<ExpectedTest> tests) {

    public static final String INVENTORY_VERSION = "2";

    public record ExpectedPage(
            String stem,
            String pageClassName,
            int expectedLocatorFields,
            int expectedActionMethods,
            int expectedAssertMethods) {
    }

    public record ExpectedTest(
            String testClassName,
            List<String> linkedPageClassNames,
            boolean generatedTier,
            List<String> tcIds) {
    }

    public static FrameworkExpectedInventory fromOutcomes(List<TcOutcome> outcomes) {
        return fromOutcomes(outcomes, "");
    }

    public static FrameworkExpectedInventory fromOutcomes(List<TcOutcome> outcomes, String testDataEnvPrefix) {
        List<TcOutcome> list = outcomes == null ? List.of() : outcomes;
        PageAccumulator accumulator = CodeWriter.accumulate(list);
        List<ExpectedPage> pages = new ArrayList<>();
        for (PageAccumulator.PageModel model : accumulator.pages().values()) {
            pages.add(new ExpectedPage(
                    model.stem(),
                    model.pageClassName(),
                    model.fields().size(),
                    model.methods().size(),
                    model.assertions().size()));
        }

        Map<String, List<TcOutcome>> groups = new LinkedHashMap<>();
        for (TcOutcome outcome : list) {
            boolean passed = outcome.status() == TcStatus.PASSED;
            groups.computeIfAbsent(CodegenTestClasses.groupKey(outcome, passed), k -> new ArrayList<>())
                    .add(outcome);
        }

        List<ExpectedTest> tests = new ArrayList<>();
        for (List<TcOutcome> members : groups.values()) {
            TcOutcome first = members.getFirst();
            boolean passed = first.status() == TcStatus.PASSED;
            String testClass = CodegenTestClasses.classNameFor(first, passed);
            List<String> linkedPages = CodeWriter.expectedUnionPageImportsForGroup(
                    members, accumulator, testDataEnvPrefix);
            List<String> tcIds = new ArrayList<>();
            for (TcOutcome o : members) {
                if (!tcIds.contains(o.tcId())) {
                    tcIds.add(o.tcId());
                }
            }
            tests.add(new ExpectedTest(testClass, List.copyOf(linkedPages), passed, List.copyOf(tcIds)));
        }
        return new FrameworkExpectedInventory(List.copyOf(pages), List.copyOf(tests));
    }

    public Set<String> expectedPageClassNames() {
        Set<String> out = new LinkedHashSet<>();
        for (ExpectedPage p : pages) {
            out.add(p.pageClassName());
        }
        return out;
    }

    public Set<String> expectedTestClassNames() {
        Set<String> out = new LinkedHashSet<>();
        for (ExpectedTest t : tests) {
            out.add(t.testClassName());
        }
        return out;
    }

    public Map<String, String> toReportMetadata() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("inventoryVersion", INVENTORY_VERSION);
        meta.put("expectedPageCount", String.valueOf(pages.size()));
        meta.put("expectedTestCount", String.valueOf(tests.size()));
        meta.put("validationMode", "inventory");
        return meta;
    }
}