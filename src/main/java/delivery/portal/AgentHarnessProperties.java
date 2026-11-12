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
    private int maxRuntimeMinutes = 15;
    private int maxRetries = 1;
    private boolean frameworkValidationEnabled = false;
    private boolean failureInvestigationEnabled = false;

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
}
