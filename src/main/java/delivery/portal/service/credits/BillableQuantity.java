package delivery.portal.service.credits;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.hunt.HuntRequest;
import delivery.portal.model.JobRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** How many priced units a job includes. The admin price is for one unit. */
public final class BillableQuantity {
    private static final ObjectMapper JSON = new ObjectMapper();

    private BillableQuantity() {
    }

    public static int count(JobRecord job) {
        if (job == null) {
            return 1;
        }
        CreditsBillingKind kind = CreditsBillingKind.fromJobKind(job.getJobKind());
        if (kind == null || kind == CreditsBillingKind.GENERATE) {
            return 1;
        }
        if (kind == CreditsBillingKind.HUNT) {
            return huntActions(job.getExcelPath());
        }
        return workbookCases(job.getExcelPath());
    }

    /** Iterations × cycles × actions. Those three caps are known before the hunt starts. */
    static int huntActions(Path requestPath) {
        HuntRequest hunt = new HuntRequest();
        if (requestPath != null && Files.isRegularFile(requestPath)) {
            try {
                HuntRequest parsed = JSON.readValue(requestPath.toFile(), HuntRequest.class);
                if (parsed != null) {
                    hunt = parsed;
                }
            } catch (Exception ignored) {
                // Unreadable request still uses the same defaults the hunt would run with.
            }
        }
        hunt.normalize();
        long actions = (long) hunt.getIterationCeiling() * hunt.getCycleCeiling() * hunt.getActionCapPerCycle();
        if (actions < 1L) {
            return 1;
        }
        return actions > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) actions;
    }

    /** Runnable test cases. A manual-only row is not charged and is not run. */
    static int workbookCases(Path excel) {
        if (excel == null || !Files.isRegularFile(excel)) {
            return 1;
        }
        try {
            List<ManualTestCase> cases = new ExcelTcReader(true).read(excel);
            int runnable = KeelPathCaseFilter.forSurface(cases, KeelPathCaseFilter.Surface.EXECUTE).size();
            return Math.max(1, runnable);
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static String breakdown(CreditsBillingKind kind, int quantity, long unitPrice) {
        if (kind == null || quantity <= 1) {
            return null;
        }
        String noun = switch (kind) {
            case EXECUTE, AUTOMATE -> "test cases";
            case HUNT -> "steps";
            case GENERATE -> "writing jobs";
        };
        return quantity + " " + noun + " × " + unitPrice;
    }
}
