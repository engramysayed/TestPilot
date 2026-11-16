package delivery.agent.harness;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

public class AgentToolRegistryTest {

    @Test(expectedExceptions = AgentHarnessException.class)
    public void requireAllowed_rejectsForbiddenTool() {
        AgentPolicy policy = new AgentPolicy(Duration.ofMinutes(5), 1, Set.of(AgentToolId.READ_WORKSPACE_FILES), List.of(), false, false);
        AgentToolRegistry.requireAllowed(policy, AgentToolId.MODIFY_GENERATED_SOURCES);
    }

    @Test
    public void requireAllowed_permitsListedTool() {
        AgentPolicy policy = new AgentPolicy(
                Duration.ofMinutes(5), 1, AgentToolRegistry.defaultsFor(AgentTaskType.FRAMEWORK_ADAPTATION_V1), List.of(), false, false);
        AgentToolRegistry.requireAllowed(policy, AgentToolId.RUN_APPROVED_MAVEN_COMMANDS);
        Assert.assertTrue(policy.allowedTools().contains(AgentToolId.RUN_APPROVED_MAVEN_COMMANDS));
    }

    @Test
    public void frameworkValidation_isReadOnly() {
        var tools = AgentToolRegistry.defaultsFor(AgentTaskType.FRAMEWORK_VALIDATION_V1);
        Assert.assertFalse(tools.contains(AgentToolId.MODIFY_GENERATED_SOURCES));
        Assert.assertTrue(tools.contains(AgentToolId.READ_WORKSPACE_FILES));
    }
}
