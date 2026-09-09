package delivery.hunt;

import delivery.excel.ManualTestCase;

import java.util.List;
import java.util.stream.Collectors;

public final class HuntBriefBuilder {
    private HuntBriefBuilder() {
    }

    public static String build(HuntRequest request, List<ManualTestCase> selectedCases) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Bug Hunter brief\n\n");
        sb.append("- Project: `").append(nullToEmpty(request.getProjectId())).append("`\n");
        sb.append("- Job: `").append(nullToEmpty(request.getJobId())).append("`\n");
        sb.append("- Base URL: ").append(nullToEmpty(request.getBaseUrl())).append("\n");
        sb.append("- Planner: ").append(nullToEmpty(request.getPlanner())).append("\n");
        sb.append("- Scenario cap: ").append(request.getScenarioCap()).append("\n");
        sb.append("- Cycle ceiling: ").append(request.getCycleCeiling()).append("\n\n");

        String us = request.getUserStory();
        if (us != null && !us.isBlank()) {
            sb.append("## User story / notes\n\n");
            sb.append(us.trim()).append("\n\n");
        }

        sb.append("## Selected test cases\n\n");
        if (selectedCases == null || selectedCases.isEmpty()) {
            sb.append("_No cases resolved — IDs: ");
            sb.append(request.getTcIds() == null ? "" : String.join(", ", request.getTcIds()));
            sb.append("_\n");
            return sb.toString();
        }
        for (ManualTestCase tc : selectedCases) {
            sb.append("### ").append(tc.tcId()).append(" — ").append(nullToEmpty(tc.title())).append("\n\n");
            if (tc.steps() != null && !tc.steps().isBlank()) {
                sb.append("**Steps**\n\n").append(tc.steps().trim()).append("\n\n");
            }
            if (tc.expectedResult() != null && !tc.expectedResult().isBlank()) {
                sb.append("**Expected**\n\n").append(tc.expectedResult().trim()).append("\n\n");
            }
            if (tc.tags() != null && !tc.tags().isBlank()) {
                sb.append("**Tags:** ").append(tc.tags().trim()).append("\n\n");
            }
        }
        sb.append("## Mission\n\n");
        sb.append("Break the feature where safe, invent up to ")
                .append(request.getScenarioCap())
                .append(" edge-case scenarios, and report defects. ")
                .append("Stop with decision `finish` when done, or when the cycle ceiling is reached.\n");
        return sb.toString();
    }

    public static List<ManualTestCase> filterSelected(List<ManualTestCase> all, List<String> tcIds) {
        if (all == null || all.isEmpty() || tcIds == null || tcIds.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> want = tcIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return all.stream().filter(tc -> want.contains(tc.tcId())).toList();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
