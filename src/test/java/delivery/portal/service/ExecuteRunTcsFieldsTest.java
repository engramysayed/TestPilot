package delivery.portal.service;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/** B16: additive failedStepIndex on GET /api/execute-runs/{id}/tcs items. */
public class ExecuteRunTcsFieldsTest {

    private static TcDraft draft(TcDraftStatus status, int blocker, String reason) {
        return new TcDraft("TC_01", "t", "1. a", "1. ok", status, List.of(), List.of(), false,
                blocker, "", reason, "", 0, "");
    }

    @Test
    public void failedStepIndexOnlyForFailuresWithAKnownStep() {
        Assert.assertEquals(ExecuteRunService.failedStepIndex(draft(TcDraftStatus.TODO, 2, "no locator")), Integer.valueOf(2));
        Assert.assertNull(ExecuteRunService.failedStepIndex(draft(TcDraftStatus.TODO, -1, "boom")));
        Assert.assertNull(ExecuteRunService.failedStepIndex(draft(TcDraftStatus.PASSED, 2, "")));
        Assert.assertNull(ExecuteRunService.failedStepIndex(null));
    }
}
