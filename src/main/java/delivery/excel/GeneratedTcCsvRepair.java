package delivery.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs common LLM CSV mistakes — especially putting each step on its own row in TC_ID.
 */
public final class GeneratedTcCsvRepair {
    private static final Pattern TC_ID_PATTERN = Pattern.compile("^TC_\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STEP_IN_ID = Pattern.compile("^-?(\\d+)\\.\\s*(.+)$");

    private GeneratedTcCsvRepair() {
    }

    public static List<ManualTestCase> repair(List<ManualTestCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return cases;
        }
        List<ManualTestCase> out = new ArrayList<>();
        List<ManualTestCase> shardBuffer = new ArrayList<>();
        for (ManualTestCase tc : cases) {
            if (isStepShardedRow(tc)) {
                shardBuffer.add(tc);
                continue;
            }
            flushShardGroup(out, shardBuffer);
            shardBuffer.clear();
            out.add(tc);
        }
        flushShardGroup(out, shardBuffer);
        return out.isEmpty() ? cases : out;
    }

    public static boolean isWellFormedTcId(String tcId) {
        if (tcId == null || tcId.isBlank()) {
            return false;
        }
        return TC_ID_PATTERN.matcher(tcId.trim()).matches();
    }

    private static boolean isStepShardedRow(ManualTestCase tc) {
        if (tc == null || tc.tcId() == null) {
            return false;
        }
        if (isWellFormedTcId(tc.tcId())) {
            return false;
        }
        return STEP_IN_ID.matcher(tc.tcId().trim()).matches();
    }

    private static void flushShardGroup(List<ManualTestCase> out, List<ManualTestCase> shardBuffer) {
        if (shardBuffer.isEmpty()) {
            return;
        }
        out.add(mergeStepShards(shardBuffer, out.size() + 1));
    }

    private static ManualTestCase mergeStepShards(List<ManualTestCase> shards, int caseNumber) {
        StringBuilder steps = new StringBuilder();
        ManualTestCase richest = shards.get(shards.size() - 1);
        for (ManualTestCase shard : shards) {
            Matcher m = STEP_IN_ID.matcher(shard.tcId().trim());
            if (!m.matches()) {
                continue;
            }
            if (steps.length() > 0) {
                steps.append('\n');
            }
            steps.append(m.group(1)).append(". ").append(m.group(2).trim());
        }

        String title = deriveTitle(shards);
        String expected = richest.expectedResult();
        if ((expected == null || expected.isBlank()) && richest.steps() != null && !richest.steps().isBlank()) {
            expected = richest.steps();
        }
        String testData = richest.testData();
        if (testData == null || testData.isBlank()) {
            testData = ExcelStepText.normalizeMultiline(richest.visualAssertion());
        }
        testData = ExcelTcReader.alignTestDataToSteps(steps.toString(), testData);

        String keelPath = normalizeKeelPath(richest.keelPath());
        // Only adopt TestData as KeelPath when it is literally a path token (column shift).
        if (isWellFormedKeelPath(richest.testData())) {
            keelPath = normalizeKeelPath(richest.testData());
            if (testData == null || testData.isBlank() || isWellFormedKeelPath(testData)) {
                testData = ExcelStepText.normalizeMultiline(richest.visualAssertion());
            }
        }

        return new ManualTestCase(
                String.format(Locale.ROOT, "TC_%02d", caseNumber),
                title,
                richest.preconditions(),
                steps.toString(),
                expected == null ? "" : expected,
                richest.priority(),
                richest.tags(),
                "",
                testData == null ? "" : testData,
                keelPath
        );
    }

    private static String deriveTitle(List<ManualTestCase> shards) {
        ManualTestCase last = shards.get(shards.size() - 1);
        Matcher m = STEP_IN_ID.matcher(last.tcId().trim());
        if (m.matches()) {
            String text = m.group(2).trim();
            if (text.toLowerCase(Locale.ROOT).startsWith("verify ")) {
                return "Invalid login feedback";
            }
            return text.length() > 72 ? text.substring(0, 69) + "…" : text;
        }
        if (last.title() != null && !last.title().isBlank()) {
            return last.title();
        }
        return "Generated test case";
    }

    private static String normalizeKeelPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "AUTOMATE";
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        if (trimmed.equals("AUTOMATE") || trimmed.equals("EXECUTE")
                || trimmed.equals("VISION_ONLY") || trimmed.equals("MANUAL")) {
            return trimmed;
        }
        return "AUTOMATE";
    }

    private static boolean isWellFormedKeelPath(String raw) {
        return raw != null && normalizeKeelPath(raw).equals(raw.trim().toUpperCase(Locale.ROOT));
    }
}
