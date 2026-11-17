package delivery.agent.validation.automate;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.validation.contract.ContractValidationReportWriter;
import delivery.agent.validation.contract.FrameworkContractValidator;
import delivery.agent.validation.contract.FrameworkExpectedInventory;
import delivery.agent.validation.contract.ValidationReport;
import delivery.job.EmitCompileCheck;
import delivery.job.JobCancelSupport;
import delivery.job.JobCancelledException;
import delivery.portal.AgentHarnessProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Automate-only acceptance gate — runs after initial EmitCompileCheck.
 * Advisory Convert validation ({@link delivery.agent.harness.convert.AgentHarnessJobBridge}) stays separate.
 */
public final class AutomateFrameworkAcceptanceGate {
    private static final Logger log = LogManager.getLogger(AutomateFrameworkAcceptanceGate.class);

    private final AgentHarnessProperties properties;
    private final BooleanSupplier cancelCheck;
    private final Duration waitBudget;
    private final AutomateFrameworkRepairBridge repairBridge;

    private AutomateFrameworkAcceptanceGate(
            AgentHarnessProperties properties,
            BooleanSupplier cancelCheck,
            Duration waitBudget,
            AutomateFrameworkRepairBridge repairBridge) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.cancelCheck = cancelCheck;
        this.waitBudget = waitBudget;
        this.repairBridge = repairBridge;
    }

    public static AutomateFrameworkAcceptanceGate forConversionJob(
            AgentHarnessProperties properties,
            BooleanSupplier cancelCheck,
            Duration waitBudget) {
        AutomateFrameworkRepairBridge bridge = null;
        if (repairEnabled(properties)) {
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forProduction(properties);
            bridge = new AutomateFrameworkRepairBridge(properties, coordinator, cancelCheck, waitBudget);
            var lookup = locatorPreferenceLookup;
            if (lookup != null) {
                bridge.withLocatorPreferenceLookup(lookup);
            }
        }
        return new AutomateFrameworkAcceptanceGate(properties, cancelCheck, waitBudget, bridge);
    }

    /** Process-wide hook — Spring wires this on boot to feed the project's locator settings into repair context. */
    private static volatile java.util.function.Function<String, AutomateFrameworkRepairBridge.LocatorPreference>
            locatorPreferenceLookup;

    public static void setLocatorPreferenceLookup(
            java.util.function.Function<String, AutomateFrameworkRepairBridge.LocatorPreference> lookup) {
        locatorPreferenceLookup = lookup;
    }

    static AutomateFrameworkAcceptanceGate forTests(AutomateFrameworkRepairBridge repairBridge) {
        var props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setAutomateFrameworkRepairEnabled(true);
        return new AutomateFrameworkAcceptanceGate(props, () -> false, Duration.ofMinutes(5), repairBridge);
    }

    public AutomateFrameworkAcceptanceOutcome run(
            Path workDir,
            Path projectDir,
            Path templateRoot,
            Path tenantProjectRoot,
            String parentJobId,
            String projectId) throws Exception {
        return run(workDir, projectDir, templateRoot, tenantProjectRoot, parentJobId, projectId, null);
    }

    public AutomateFrameworkAcceptanceOutcome run(
            Path workDir,
            Path projectDir,
            Path templateRoot,
            Path tenantProjectRoot,
            String parentJobId,
            String projectId,
            FrameworkExpectedInventory expectedInventory) throws Exception {
        JobCancelSupport.checkCancelled(cancelCheck);
        Path reportRoot = tenantProjectRoot == null ? projectDir : tenantProjectRoot;
        String reportParent = parentJobId == null ? "acceptance" : parentJobId + "/acceptance";

        ValidationReport report =
                FrameworkContractValidator.validateWorkspace(projectDir, templateRoot, expectedInventory);
        ContractValidationReportWriter.writeContractReport(reportRoot, reportParent, report);
        if (report.passed()) {
            return recordAndReturn(
                    workDir, null, AutomateFrameworkAcceptanceOutcome.accepted(report));
        }

        boolean repairEnabled = repairEnabled(properties);
        if (!repairEnabled) {
            return recordAndReturn(
                    workDir,
                    null,
                    AutomateFrameworkAcceptanceOutcome.rejected(
                            AutomateFrameworkAcceptanceStatus.REPAIR_NOT_ENABLED,
                            report,
                            null,
                            "",
                            "mandatory contract violations; automate-framework-repair disabled"));
        }
        if (repairBridge == null) {
            return recordAndReturn(
                    workDir,
                    null,
                    AutomateFrameworkAcceptanceOutcome.rejected(
                            AutomateFrameworkAcceptanceStatus.REPAIR_UNAVAILABLE,
                            report,
                            null,
                            "",
                            "repair bridge unavailable"));
        }

        FrameworkAcceptanceWorkspace.snapshotOriginal(workDir, projectDir);
        Path repairRoot = FrameworkAcceptanceWorkspace.createRepairCandidate(workDir, projectDir);
        FrameworkRepairOperationalTrace operationalTrace = null;

        if (templateRoot != null) {
            repairBridge.withTemplateRootPath(templateRoot.toAbsolutePath().toString());
        }

        try {
            var repair = repairBridge.attemptRepair(
                    reportRoot,
                    repairRoot,
                    new AutomateFrameworkRepairBridge.ConversionJobContext(parentJobId, projectId),
                    report);
            operationalTrace = repair.operationalTrace();
            if (repair.terminalStatus() == AgentTaskStatus.INTERRUPTED) {
                return recordAndReturn(
                        workDir,
                        operationalTrace,
                        AutomateFrameworkAcceptanceOutcome.rejected(
                                AutomateFrameworkAcceptanceStatus.REPAIR_INTERRUPTED,
                                report,
                                null,
                                repair.taskId(),
                                repair.summary()));
            }
            if (!repair.succeeded()) {
                return recordAndReturn(
                        workDir,
                        operationalTrace,
                        AutomateFrameworkAcceptanceOutcome.rejected(
                                AutomateFrameworkAcceptanceStatus.REPAIR_FAILED,
                                report,
                                null,
                                repair.taskId(),
                                repair.summary()));
            }

            if (repair.providerId() == AgentProviderId.CURSOR) {
                long reconStart = System.nanoTime();
                try {
                    FrameworkRepairWorkspaceReconciler.Manifest manifest =
                            new FrameworkRepairWorkspaceReconciler().reconcile(
                                    workDir,
                                    repair.projectRoot(),
                                    repair.parentJobId(),
                                    repair.taskId(),
                                    repairRoot,
                                    repair.agentWorkspace());
                    writeReconciliationManifest(workDir, manifest);
                    long reconMs = (System.nanoTime() - reconStart) / 1_000_000L;
                    if (operationalTrace != null) {
                        operationalTrace = operationalTrace.withReconciliationMs(reconMs);
                    }
                } catch (FrameworkRepairReconciliationException e) {
                    log.warn("framework repair reconciliation failed for {}: {}", repair.taskId(), e.getMessage());
                    if (operationalTrace != null) {
                        long reconMs = (System.nanoTime() - reconStart) / 1_000_000L;
                        operationalTrace = operationalTrace.withReconciliationMs(reconMs);
                    }
                    return recordAndReturn(
                            workDir,
                            operationalTrace,
                            AutomateFrameworkAcceptanceOutcome.rejected(
                                    AutomateFrameworkAcceptanceStatus.RECONCILIATION_FAILED,
                                    report,
                                    null,
                                    repair.taskId(),
                                    e.getMessage()));
                }
            }

            JobCancelSupport.checkCancelled(cancelCheck);
            try {
                EmitCompileCheck.runIfEnabled(repairRoot, Duration.ofMinutes(10), cancelCheck);
                Path compileOk = FrameworkAcceptanceWorkspace.acceptanceRoot(workDir).resolve("emit-compile-check.ok");
                Files.createDirectories(compileOk.getParent());
                Files.writeString(compileOk, repairRoot.toAbsolutePath().normalize().toString());
            } catch (Exception e) {
                Path compileFail = repairRoot.resolve("docs/EMIT_COMPILE_FAIL.log");
                String detail = e.getMessage();
                if (Files.isRegularFile(compileFail)) {
                    detail = detail + " log=" + compileFail.toAbsolutePath();
                }
                return recordAndReturn(
                        workDir,
                        operationalTrace,
                        AutomateFrameworkAcceptanceOutcome.rejected(
                                AutomateFrameworkAcceptanceStatus.COMPILE_FAILED,
                                report,
                                null,
                                repair.taskId(),
                                detail));
            }

            ValidationReport revalidated =
                    FrameworkContractValidator.validateWorkspace(repairRoot, templateRoot, expectedInventory);
            ContractValidationReportWriter.writeContractReport(
                    reportRoot, reportParent + "-revalidate", revalidated);
            if (!revalidated.passed()) {
                return recordAndReturn(
                        workDir,
                        operationalTrace,
                        AutomateFrameworkAcceptanceOutcome.rejected(
                                AutomateFrameworkAcceptanceStatus.REVALIDATION_FAILED,
                                report,
                                revalidated,
                                repair.taskId(),
                                "independent revalidation failed"));
            }

            // B4 closed: run the architecture assessor as a promotion gate. A repair that compiles
            // and passes contract v2 can still ship an empty-body test class or a page with no actions
            // — the assessor catches those. Opt-out via delivery.agent-harness.automate-framework-repair.architecture-gate-enabled=false.
            if (properties.isAutomateFrameworkRepairArchitectureGateEnabled()) {
                FrameworkRepairArchitectureAssessor.ArchitectureQualityReport arch =
                        FrameworkRepairArchitectureAssessor.assess(repairRoot, expectedInventory);
                if (!arch.functionallyComplete()) {
                    StringBuilder summary = new StringBuilder("architecture gaps: ");
                    arch.findings().forEach(f -> {
                        if (f.functionalGap()) summary.append(f.code()).append(' ');
                    });
                    return recordAndReturn(
                            workDir,
                            operationalTrace,
                            AutomateFrameworkAcceptanceOutcome.rejected(
                                    AutomateFrameworkAcceptanceStatus.ARCHITECTURE_GAPS,
                                    report,
                                    revalidated,
                                    repair.taskId(),
                                    summary.toString().trim()));
                }
            }

            FrameworkAcceptanceWorkspace.promoteRepairCandidate(repairRoot, projectDir);
            log.info("Automate framework accepted after repair task {}", repair.taskId());
            return recordAndReturn(
                    workDir, operationalTrace, AutomateFrameworkAcceptanceOutcome.accepted(revalidated));
        } catch (JobCancelledException e) {
            throw e;
        } catch (AgentHarnessException e) {
            if (operationalTrace == null && repairBridge != null) {
                operationalTrace = repairBridge.lastRepairOperationalTrace();
            }
            if ("INTERRUPTED".equals(e.getMessage())) {
                return recordAndReturn(
                        workDir,
                        operationalTrace,
                        AutomateFrameworkAcceptanceOutcome.rejected(
                                AutomateFrameworkAcceptanceStatus.REPAIR_INTERRUPTED,
                                report,
                                null,
                                "",
                                e.getMessage()));
            }
            return recordAndReturn(
                    workDir,
                    operationalTrace,
                    AutomateFrameworkAcceptanceOutcome.rejected(
                            AutomateFrameworkAcceptanceStatus.REPAIR_FAILED,
                            report,
                            null,
                            "",
                            e.getMessage()));
        }
    }

    private static AutomateFrameworkAcceptanceOutcome recordAndReturn(
            Path workDir,
            FrameworkRepairOperationalTrace trace,
            AutomateFrameworkAcceptanceOutcome outcome) {
        try {
            FrameworkRepairTerminalReason acceptance =
                    FrameworkRepairTerminalReasonClassifier.fromAcceptanceStatus(outcome.status());
            FrameworkRepairOperationalTrace merged = trace == null
                    ? FrameworkRepairOperationalTrace.builder().acceptanceTerminalReason(acceptance).build()
                    : trace.withAcceptanceTerminalReason(acceptance);
            FrameworkRepairOperationalReportWriter.write(workDir, merged, outcome);
        } catch (Exception e) {
            log.debug("framework repair operational report skipped: {}", e.getMessage());
        }
        return outcome;
    }

    private static boolean repairEnabled(AgentHarnessProperties properties) {
        return properties != null
                && properties.isEnabled()
                && properties.isAutomateFrameworkRepairEnabled();
    }

    private static void writeReconciliationManifest(Path workDir, FrameworkRepairWorkspaceReconciler.Manifest manifest)
            throws IOException {
        if (workDir == null || manifest == null) {
            return;
        }
        Path dest = FrameworkAcceptanceWorkspace.acceptanceRoot(workDir).resolve("reconciliation-manifest.json");
        Files.createDirectories(dest.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(dest.toFile(), manifest);
    }
}