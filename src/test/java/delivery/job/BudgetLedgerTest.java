package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class BudgetLedgerTest {

    @Test
    public void concurrentReservesRespectHardCap() throws Exception {
        Path dir = Files.createTempDirectory("budget");
        BudgetLedger ledger = new BudgetLedger(dir.resolve("budget.json"), new BudgetLedger.Limits(1, 1));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String jobId = "job_" + i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    ledger.reserve(jobId, 1, BudgetLedger.CostKind.ESTIMATED);
                    admitted.incrementAndGet();
                } catch (BudgetLedger.Rejected e) {
                    rejected.incrementAndGet();
                    Assert.assertEquals(e.code(), "BUDGET_EXCEEDED");
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdownNow();
        Assert.assertEquals(admitted.get(), 1);
        Assert.assertEquals(rejected.get(), 1);
    }

    @Test
    public void retryOfSameJobDoesNotConsumeASecondAllowance() throws Exception {
        Path dir = Files.createTempDirectory("budget-retry");
        BudgetLedger ledger = new BudgetLedger(dir.resolve("budget.json"), new BudgetLedger.Limits(1, 1));
        BudgetLedger.Reservation first = ledger.reserve("job_a", 1, BudgetLedger.CostKind.ESTIMATED);
        BudgetLedger.Reservation retry = ledger.reserve("job_a", 1, BudgetLedger.CostKind.ESTIMATED);
        Assert.assertEquals(retry.estimatedUnits(), first.estimatedUnits());
        Assert.assertThrows(BudgetLedger.Rejected.class,
                () -> ledger.reserve("job_b", 1, BudgetLedger.CostKind.ESTIMATED));
    }

    @Test
    public void unknownCostIsExplicitAndReconcileDistinguishesFinalSpend() throws Exception {
        Path dir = Files.createTempDirectory("budget-unknown");
        BudgetLedger ledger = new BudgetLedger(dir.resolve("budget.json"), new BudgetLedger.Limits(10, 8));
        try {
            ledger.reserve("job_u", 1, BudgetLedger.CostKind.UNKNOWN);
            Assert.fail("expected unknown cost rejection");
        } catch (BudgetLedger.Rejected e) {
            Assert.assertEquals(e.code(), "COST_UNKNOWN");
        }
        ledger.reserve("job_ok", 2, BudgetLedger.CostKind.ESTIMATED);
        BudgetLedger.UsageRecord usage = ledger.reconcile("job_ok", 3);
        Assert.assertEquals(usage.estimatedKind(), BudgetLedger.CostKind.ESTIMATED);
        Assert.assertEquals(usage.reconciledKind(), BudgetLedger.CostKind.RECONCILED);
        Assert.assertEquals(usage.reconciledUnits(), 3);
        Assert.assertTrue(ledger.history().stream().anyMatch(h -> "job_ok".equals(h.jobId())));
        BudgetLedger.Snapshot snap = ledger.snapshot();
        Assert.assertEquals(snap.reservedUnits(), 0L);
        Assert.assertEquals(snap.hardCapUnits(), 10L);
        Assert.assertEquals(snap.warningUnits(), 8L);
    }
}
