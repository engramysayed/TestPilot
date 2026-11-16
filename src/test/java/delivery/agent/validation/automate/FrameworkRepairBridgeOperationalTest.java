package delivery.agent.validation.automate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.FakeAgentRuntime;
import delivery.agent.harness.SlowFakeAgentRuntime;
import delivery.agent.validation.contract.FrameworkContractValidator;
import delivery.job.JobCancelledException;
import delivery.portal.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Bridge + gate operational trace without Cursor. */
public class FrameworkRepairBridgeOperationalTest {

  private String priorCompileCheck;

  @BeforeMethod
  public void setUp() {
    priorCompileCheck = System.getProperty("delivery.emit-compile-check");
    System.setProperty("delivery.emit-compile-check", "false");
  }

  @AfterMethod
  public void tearDown() {
    if (priorCompileCheck == null) {
      System.clearProperty("delivery.emit-compile-check");
    } else {
      System.setProperty("delivery.emit-compile-check", priorCompileCheck);
    }
  }

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration SHORT_BRIDGE = Duration.ofMillis(800);
  private static final Path TEMPLATE = Path.of("customer-framework-template").toAbsolutePath().normalize();

  @Test
  public void sidecarStarts_quickly_successTrace() throws Exception {
    Path work = Files.createTempDirectory("ops-bridge-fast-");
    Path project = AutomateFrameworkAcceptanceGateTest.scaffoldProjectPublic(work, false);
    var props = harnessProps(new FakeAgentRuntime(), 15);
    AgentHarnessCoordinator coord = new AgentHarnessCoordinator(
            null, null, null, props, runtimeMap(new AutomateFrameworkAcceptanceGateTest.FixingFakeRuntime(project)));
    var bridge = new AutomateFrameworkRepairBridge(props, coord, () -> false, Duration.ofMinutes(5));
    var gate = AutomateFrameworkAcceptanceGate.forTests(bridge);
    var outcome = gate.run(work, project, TEMPLATE, work, "j-fast-gate", "p");
    Assert.assertTrue(outcome.accepted());
    JsonNode operational = readOperational(work);
    Assert.assertEquals(
        operational.path("trace").path("acceptanceTerminalReason").asText(),
        FrameworkRepairTerminalReason.SUCCESS.name());
    Assert.assertTrue(operational.path("trace").path("bridgeElapsedMs").asLong() >= 0);
    Assert.assertTrue(operational.path("trace").path("agentRunStarted").asBoolean());
  }

  @Test
  public void bridgeWaitTimeout_beforeRunIds() throws Exception {
    var bridge = bridgeWith(Duration.ofMillis(500), new SlowStartFakeRuntime(5_000), () -> false);
    assertBridgeTimeout(bridge, false, FrameworkRepairTerminalReason.BRIDGE_WAIT_TIMEOUT);
  }

  @Test
  public void bridgeWaitTimeout_afterRunIds() throws Exception {
    var bridge = bridgeWith(SHORT_BRIDGE, new SlowFakeAgentRuntime(5_000), () -> false);
    FrameworkRepairOperationalTrace trace = assertBridgeTimeout(bridge, true, FrameworkRepairTerminalReason.BRIDGE_WAIT_TIMEOUT);
    Assert.assertNotNull(trace.agentId());
    Assert.assertNotNull(trace.agentRunId());
  }

  @Test
  public void explicitJobCancel() throws Exception {
    AtomicBoolean cancel = new AtomicBoolean();
    Thread watcher = new Thread(() -> {
      try {
        Thread.sleep(120);
        cancel.set(true);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    watcher.start();
    var bridge = bridgeWith(Duration.ofMinutes(1), new SlowFakeAgentRuntime(30_000), cancel::get);
    try {
      invokeAttempt(bridge);
      Assert.fail("expected job cancel");
    } catch (JobCancelledException expected) {
      // Policy A: cooperative cancel
    } finally {
      watcher.join(2_000);
    }
    FrameworkRepairOperationalTrace trace = bridge.lastRepairOperationalTrace();
    Assert.assertEquals(trace.bridgeTerminalReason(), FrameworkRepairTerminalReason.EXPLICIT_JOB_CANCEL);
  }

  @Test
  public void reconcilePollExhausted_reasonOnInterrupted() throws Exception {
    var bridge = bridgeWith(
            Duration.ofMinutes(1),
            new TerminalStatusRuntime(AgentTaskStatus.INTERRUPTED, "reconcile poll budget exhausted"),
            () -> false);
    try {
      invokeAttempt(bridge);
      Assert.fail("expected INTERRUPTED");
    } catch (AgentHarnessException e) {
      Assert.assertEquals(e.getMessage(), "INTERRUPTED");
    }
    Assert.assertEquals(
        bridge.lastRepairOperationalTrace().bridgeTerminalReason(),
        FrameworkRepairTerminalReason.RECONCILE_POLL_EXHAUSTED);
  }

  @Test
  public void cursorRunFailure_runtimeError() throws Exception {
    var bridge = bridgeWith(Duration.ofMinutes(1), new ThrowingGetResultRuntime("cursor start failed: unavailable"), () -> false);
    try {
      invokeAttempt(bridge);
      Assert.fail("expected harness failure");
    } catch (AgentHarnessException e) {
      Assert.assertEquals(e.getMessage(), "cursor start failed: unavailable");
    }
    Assert.assertEquals(
        bridge.lastRepairOperationalTrace().bridgeTerminalReason(),
        FrameworkRepairTerminalReason.CURSOR_RUN_FAILURE);
  }

  @Test
  public void compileRevalidationAndPromotion_operationalReport() throws Exception {
    Path work = Files.createTempDirectory("ops-gate-stages-");
    Path project = AutomateFrameworkAcceptanceGateTest.scaffoldProjectPublic(work, false);
    var props = harnessProps(new FakeAgentRuntime(), 15);
    var gateFail = AutomateFrameworkAcceptanceGate.forConversionJob(props, () -> false, Duration.ofMinutes(5));
    var revalFail = gateFail.run(work, project, TEMPLATE, work, "j-comp", "p");
    Assert.assertEquals(revalFail.status(), AutomateFrameworkAcceptanceStatus.REVALIDATION_FAILED);
    JsonNode opFail = readOperational(work);
    Assert.assertEquals(
        opFail.path("trace").path("acceptanceTerminalReason").asText(),
        FrameworkRepairTerminalReason.REVALIDATION_FAILURE.name());

    Path work2 = Files.createTempDirectory("ops-gate-promote-");
    Path project2 = AutomateFrameworkAcceptanceGateTest.scaffoldProjectPublic(work2, false);
    AgentHarnessCoordinator coord = new AgentHarnessCoordinator(
            null, null, null, props, runtimeMap(new AutomateFrameworkAcceptanceGateTest.FixingFakeRuntime(project2)));
    var bridge = new AutomateFrameworkRepairBridge(props, coord, () -> false, Duration.ofMinutes(5));
    var gateOk = AutomateFrameworkAcceptanceGate.forTests(bridge);
    var promoted = gateOk.run(work2, project2, TEMPLATE, work2, "j-ok", "p");
    Assert.assertTrue(promoted.accepted());
    Assert.assertEquals(
        readOperational(work2).path("trace").path("acceptanceTerminalReason").asText(),
        FrameworkRepairTerminalReason.SUCCESS.name());
  }

  @Test
  public void compileFailure_operationalReport() throws Exception {
    String prior = System.getProperty("delivery.emit-compile-check");
    System.setProperty("delivery.emit-compile-check", "true");
    try {
      Path work = Files.createTempDirectory("ops-compile-fail-");
      Path project = AutomateFrameworkAcceptanceGateTest.scaffoldProjectPublic(work, false);
      var props = harnessProps(new FakeAgentRuntime(), 15);
      AgentHarnessCoordinator coord = new AgentHarnessCoordinator(
              null, null, null, props, runtimeMap(new AutomateFrameworkAcceptanceGateTest.FixingFakeRuntime(project)));
      var bridge = new AutomateFrameworkRepairBridge(props, coord, () -> false, Duration.ofMinutes(5));
      var gate = AutomateFrameworkAcceptanceGate.forTests(bridge);
      Path repairRoot = FrameworkAcceptanceWorkspace.createRepairCandidate(work, project);
      Files.writeString(repairRoot.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
      var outcome = gate.run(work, project, TEMPLATE, work, "j-compile-fail", "p");
      Assert.assertEquals(outcome.status(), AutomateFrameworkAcceptanceStatus.COMPILE_FAILED);
      Assert.assertEquals(
          readOperational(work).path("trace").path("acceptanceTerminalReason").asText(),
          FrameworkRepairTerminalReason.COMPILE_FAILURE.name());
    } finally {
      if (prior == null) {
        System.clearProperty("delivery.emit-compile-check");
      } else {
        System.setProperty("delivery.emit-compile-check", prior);
      }
    }
  }

  private static FrameworkRepairOperationalTrace assertBridgeTimeout(
          AutomateFrameworkRepairBridge bridge,
          boolean expectRunStarted,
          FrameworkRepairTerminalReason reason) throws Exception {
    try {
      invokeAttempt(bridge);
      Assert.fail("expected bridge timeout");
    } catch (AgentHarnessException e) {
      Assert.assertEquals(e.getMessage(), "SIDECAR_TIMEOUT");
    }
    FrameworkRepairOperationalTrace trace = bridge.lastRepairOperationalTrace();
    Assert.assertEquals(trace.bridgeTerminalReason(), reason);
    Assert.assertNotNull(trace.bridgeBudgetMs());
    Assert.assertNotNull(trace.bridgeElapsedMs());
    Assert.assertNotNull(trace.bridgeBudgetUtilizationPct());
    Assert.assertTrue(trace.bridgeBudgetUtilizationPct() >= 90.0);
    if (expectRunStarted) {
      Assert.assertTrue(trace.agentRunStarted());
    } else {
      Assert.assertFalse(Boolean.TRUE.equals(trace.agentRunStarted()));
    }
    return trace;
  }

  private static AutomateFrameworkRepairBridge bridgeWith(
          Duration waitBudget, AgentRuntime runtime, BooleanSupplier cancel) {
    var props = harnessProps(runtime, 15);
    return new AutomateFrameworkRepairBridge(
            props, coordinator(props, runtime), cancel, waitBudget);
  }

  private static AutomateFrameworkRepairBridge.RepairResult invokeAttempt(AutomateFrameworkRepairBridge bridge)
          throws Exception {
    Path work = Files.createTempDirectory("ops-invoke-");
    Path project = AutomateFrameworkAcceptanceGateTest.scaffoldProjectPublic(work, false);
    var report = FrameworkContractValidator.validateWorkspace(project, TEMPLATE);
    Assert.assertFalse(report.passed());
    Path repairRoot = FrameworkAcceptanceWorkspace.createRepairCandidate(work, project);
    return bridge.attemptRepair(
            work, repairRoot, new AutomateFrameworkRepairBridge.ConversionJobContext("job-ops", "proj"), report);
  }

  private static JsonNode readOperational(Path work) throws Exception {
    Path report = FrameworkRepairOperationalReportWriter.reportPath(work);
    Assert.assertTrue(Files.isRegularFile(report), "missing " + report);
    return JSON.readTree(Files.readString(report));
  }

  private static AgentHarnessProperties harnessProps(AgentRuntime runtime, int maxRuntimeMinutes) {
    var p = new AgentHarnessProperties();
    p.setEnabled(true);
    p.setAutomateFrameworkRepairEnabled(true);
    p.setCursorEnabled(false);
    p.setMaxRuntimeMinutes(maxRuntimeMinutes);
    return p;
  }

  private static AgentHarnessCoordinator coordinator(AgentHarnessProperties props) {
    return coordinator(props, new FakeAgentRuntime());
  }

  private static AgentHarnessCoordinator coordinator(AgentHarnessProperties props, AgentRuntime runtime) {
    return new AgentHarnessCoordinator(null, null, null, props, runtimeMap(runtime));
  }

  private static EnumMap<AgentProviderId, AgentRuntime> runtimeMap(AgentRuntime runtime) {
    EnumMap<AgentProviderId, AgentRuntime> map = new EnumMap<>(AgentProviderId.class);
    map.put(AgentProviderId.FAKE, runtime);
    return map;
  }

  static final class SlowStartFakeRuntime implements AgentRuntime {
    private final long delayMs;
    private final FakeAgentRuntime delegate = new FakeAgentRuntime();

    SlowStartFakeRuntime(long delayMs) {
      this.delayMs = delayMs;
    }

    @Override
    public AgentProviderId providerId() {
      return AgentProviderId.FAKE;
    }

    @Override
    public AgentRunHandle start(AgentTask task) {
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AgentHarnessException("interrupted during slow start");
      }
      return delegate.start(task);
    }

    @Override
    public AgentRunHandle resume(String agentId, String runId) {
      return delegate.resume(agentId, runId);
    }

    @Override
    public void cancel(String agentId, String runId) {
      delegate.cancel(agentId, runId);
    }

    @Override
    public AgentRunResult getResult(String agentId, String runId) {
      return delegate.getResult(agentId, runId);
    }
  }

  static final class ThrowingGetResultRuntime implements AgentRuntime {
    private final String message;
    private final FakeAgentRuntime delegate = new FakeAgentRuntime();

    ThrowingGetResultRuntime(String message) {
      this.message = message;
    }

    @Override
    public AgentProviderId providerId() {
      return AgentProviderId.FAKE;
    }

    @Override
    public AgentRunHandle start(AgentTask task) {
      return delegate.start(task);
    }

    @Override
    public AgentRunHandle resume(String agentId, String runId) {
      return delegate.resume(agentId, runId);
    }

    @Override
    public void cancel(String agentId, String runId) {
      delegate.cancel(agentId, runId);
    }

    @Override
    public AgentRunResult getResult(String agentId, String runId) {
      throw new AgentHarnessException(message);
    }
  }

  static final class TerminalStatusRuntime implements AgentRuntime {
    private final AgentTaskStatus terminal;
    private final String summary;

    TerminalStatusRuntime(AgentTaskStatus terminal, String summary) {
      this.terminal = terminal;
      this.summary = summary;
    }

    @Override
    public AgentProviderId providerId() {
      return AgentProviderId.FAKE;
    }

    @Override
    public AgentRunHandle start(AgentTask task) {
      String agentId = "term-agent-" + task.taskId();
      String runId = UUID.randomUUID().toString();
      return new AgentRunHandle(agentId, runId, Instant.now());
    }

    @Override
    public AgentRunHandle resume(String agentId, String runId) {
      return new AgentRunHandle(agentId, runId, Instant.now());
    }

    @Override
    public void cancel(String agentId, String runId) {
    }

    @Override
    public AgentRunResult getResult(String agentId, String runId) {
      return new AgentRunResult(terminal, summary, Map.of(), List.of(), "");
    }
  }
}