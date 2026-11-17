package delivery.portal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature flags for the unified agent harness ({@code delivery.agent-harness.*}).
 * All defaults false — existing workflows unchanged when disabled.
 */
@ConfigurationProperties(prefix = "delivery.agent-harness")
public class AgentHarnessProperties {
    private boolean enabled = false;
    private boolean cursorEnabled = false;
    private int maxConcurrentRuns = 2;
    /** B6: process-wide fair-share cap across all parent jobs. Default = 2 × conversionExecutor max. */
    private int globalMaxConcurrentAgents = 4;
    private int maxRuntimeMinutes = 15;
    private int maxRetries = 1;
    private boolean frameworkValidationEnabled = false;
    private boolean failureInvestigationEnabled = false;
    private boolean tcValidationEnabled = false;
    private boolean tcValidationCursorEscalationEnabled = false;
    private boolean executionRecoveryCursorEscalationEnabled = false;
    private boolean automateFrameworkRepairEnabled = false;
    /** B4: run the architecture assessor as a publish gate on repaired frameworks (default on). */
    private boolean automateFrameworkRepairArchitectureGateEnabled = true;
    private String cursorCommand = "node tools/cursor-agent/agent.mjs";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCursorEnabled() {
        return cursorEnabled;
    }

    public void setCursorEnabled(boolean cursorEnabled) {
        this.cursorEnabled = cursorEnabled;
    }

    public int getMaxConcurrentRuns() {
        return maxConcurrentRuns;
    }

    public void setMaxConcurrentRuns(int maxConcurrentRuns) {
        this.maxConcurrentRuns = Math.max(1, maxConcurrentRuns);
    }

    public int getGlobalMaxConcurrentAgents() {
        return globalMaxConcurrentAgents;
    }

    public void setGlobalMaxConcurrentAgents(int v) {
        this.globalMaxConcurrentAgents = Math.max(1, v);
    }

    public int getMaxRuntimeMinutes() {
        return maxRuntimeMinutes;
    }

    public void setMaxRuntimeMinutes(int maxRuntimeMinutes) {
        this.maxRuntimeMinutes = Math.max(1, maxRuntimeMinutes);
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public boolean isFrameworkValidationEnabled() {
        return frameworkValidationEnabled;
    }

    public void setFrameworkValidationEnabled(boolean frameworkValidationEnabled) {
        this.frameworkValidationEnabled = frameworkValidationEnabled;
    }

    public boolean isFailureInvestigationEnabled() {
        return failureInvestigationEnabled;
    }

    public void setFailureInvestigationEnabled(boolean failureInvestigationEnabled) {
        this.failureInvestigationEnabled = failureInvestigationEnabled;
    }

    public boolean isTcValidationEnabled() {
        return tcValidationEnabled;
    }

    public void setTcValidationEnabled(boolean tcValidationEnabled) {
        this.tcValidationEnabled = tcValidationEnabled;
    }

    public boolean isTcValidationCursorEscalationEnabled() {
        return tcValidationCursorEscalationEnabled;
    }

    public void setTcValidationCursorEscalationEnabled(boolean tcValidationCursorEscalationEnabled) {
        this.tcValidationCursorEscalationEnabled = tcValidationCursorEscalationEnabled;
    }

    public boolean isExecutionRecoveryCursorEscalationEnabled() {
        return executionRecoveryCursorEscalationEnabled;
    }

    public void setExecutionRecoveryCursorEscalationEnabled(boolean executionRecoveryCursorEscalationEnabled) {
        this.executionRecoveryCursorEscalationEnabled = executionRecoveryCursorEscalationEnabled;
    }

    public boolean isAutomateFrameworkRepairEnabled() {
        return automateFrameworkRepairEnabled;
    }

    public void setAutomateFrameworkRepairEnabled(boolean automateFrameworkRepairEnabled) {
        this.automateFrameworkRepairEnabled = automateFrameworkRepairEnabled;
    }

    public boolean isAutomateFrameworkRepairArchitectureGateEnabled() {
        return automateFrameworkRepairArchitectureGateEnabled;
    }

    public void setAutomateFrameworkRepairArchitectureGateEnabled(boolean v) {
        this.automateFrameworkRepairArchitectureGateEnabled = v;
    }

    public String getCursorCommand() {
        return cursorCommand;
    }

    public void setCursorCommand(String cursorCommand) {
        this.cursorCommand = cursorCommand == null || cursorCommand.isBlank()
                ? "node tools/cursor-agent/agent.mjs"
                : cursorCommand.trim();
    }
}
