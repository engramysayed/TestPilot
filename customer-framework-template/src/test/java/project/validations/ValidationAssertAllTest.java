package project.validations;

import org.testng.Assert;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.TestNG;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import project.validations.support.FalseCustomerAssertionSample;
import project.validations.support.PassingCustomerAssertionSample;
import project.validations.support.QuitProbeDriverFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * F00: a false customer assertion must fail TestNG, then leave later tests clean.
 */
public class ValidationAssertAllTest {

    @BeforeMethod
    public void isolateThreadState() {
        Validation.reset();
    }

    @Test
    public void deliberatelyFalseAssertionPropagatesFromAssertAll() {
        Validation validation = new Validation(null);
        validation.softTrue(false, "AUDIT: deliberately false customer assertion");
        AssertionError thrown = null;
        try {
            Validation.assertAll();
        } catch (AssertionError expected) {
            thrown = expected;
        }
        Assert.assertNotNull(thrown, "assertAll must throw AssertionError for a false customer assertion");
        Assert.assertTrue(
                thrown.getMessage() != null
                        && thrown.getMessage().contains("deliberately false customer assertion"),
                "failure must name the customer assertion: " + thrown.getMessage());
    }

    @Test
    public void subsequentPassingTestDoesNotInheritPriorFailure() {
        Validation failed = new Validation(null);
        failed.softTrue(false, "AUDIT: first test false assertion");
        AssertionError thrown = null;
        try {
            Validation.assertAll();
        } catch (AssertionError expected) {
            thrown = expected;
        }
        Assert.assertNotNull(thrown, "first assertAll must throw");
        Assert.assertTrue(thrown.getMessage().contains("first test false assertion"));

        Validation passed = new Validation(null);
        passed.softTrue(true, "second test true assertion");
        Validation.assertAll();
    }

    @Test
    public void parallelTestsDoNotExchangeFailures() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<Throwable> passingSide = new AtomicReference<>();
        AtomicReference<Throwable> failingSide = new AtomicReference<>();

        Thread failing = new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                new Validation(null).softTrue(false, "AUDIT: parallel false assertion");
                try {
                    Validation.assertAll();
                    failingSide.set(new AssertionError("failing thread must throw"));
                } catch (AssertionError expected) {
                    if (expected.getMessage() == null
                            || !expected.getMessage().contains("parallel false assertion")) {
                        failingSide.set(expected);
                    }
                }
            } catch (Exception e) {
                failingSide.set(e);
            } finally {
                done.countDown();
            }
        }, "f00-failing");

        Thread passing = new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                new Validation(null).softTrue(true, "parallel true assertion");
                Validation.assertAll();
            } catch (Throwable e) {
                passingSide.set(e);
            } finally {
                done.countDown();
            }
        }, "f00-passing");

        failing.start();
        passing.start();
        Assert.assertTrue(done.await(10, TimeUnit.SECONDS), "parallel assertion threads timed out");
        Assert.assertNull(failingSide.get(), "failing thread mishandled: " + failingSide.get());
        Assert.assertNull(passingSide.get(), "passing thread inherited a failure: " + passingSide.get());
    }

    @Test
    public void nestedSuiteMarksFalseAssertionFailedAndKeepsLaterTestPassed() {
        TestNG tng = new TestNG(false);
        tng.setDefaultSuiteName("f00-assertion-probe");
        tng.setUseDefaultListeners(false);
        tng.setTestClasses(new Class[]{
                FalseCustomerAssertionSample.class,
                PassingCustomerAssertionSample.class
        });
        tng.addListener(new AssertionResultListener());
        tng.run();

        Assert.assertTrue(tng.hasFailure(), "deliberately false assertion must fail the nested TestNG suite");
        Assert.assertNotEquals(tng.getStatus(), 0, "nested Maven-equivalent status must be nonzero");
        Assert.assertFalse(tng.hasSkip(), "assertion failure must not be recorded as a skip");
    }

    @Test
    public void browserQuitRunsWhenAssertAllFails() {
        QuitProbeDriverFactory.quitCalls = 0;
        boolean thrown = false;
        try {
            new Validation(null).softTrue(false, "AUDIT: teardown still quits");
            try {
                Validation.assertAll();
            } finally {
                QuitProbeDriverFactory.quit();
            }
        } catch (AssertionError expected) {
            thrown = true;
            Assert.assertTrue(expected.getMessage().contains("teardown still quits"));
        }
        Assert.assertTrue(thrown, "assertAll must still fail the test");
        Assert.assertEquals(QuitProbeDriverFactory.quitCalls, 1, "browser cleanup must run after assertion failure");
    }

    /** Mirrors production listener: record assertion failure on the TestNG result without swallowing it. */
    public static final class AssertionResultListener implements IInvokedMethodListener {
        @Override
        public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
            if (!method.isTestMethod()) {
                return;
            }
            try {
                Validation.assertAll();
            } catch (AssertionError e) {
                testResult.setStatus(ITestResult.FAILURE);
                testResult.setThrowable(e);
            }
        }
    }
}
