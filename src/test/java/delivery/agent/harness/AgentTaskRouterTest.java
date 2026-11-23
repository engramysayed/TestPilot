package delivery.agent.harness;

import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class AgentTaskRouterTest {

    @Test
    public void selectsFake_whenHarnessOrCursorDisabled() throws Exception {
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(false);
        AgentTaskRouter router = new AgentTaskRouter(props);
        Path ws = Files.createTempDirectory("router-ws");
        AgentTaskPlan plan = router.plan(AgentTaskType.FRAMEWORK_VALIDATION_V1, ws);
        Assert.assertEquals(plan.providerId(), AgentProviderId.FAKE);
        Assert.assertFalse(plan.policy().allowedTools().contains(AgentToolId.MODIFY_GENERATED_SOURCES));
        Assert.assertTrue(plan.policy().allowedTools().contains(AgentToolId.READ_WORKSPACE_FILES));
        Assert.assertTrue(plan.policy().isWorkspaceAllowed(ws));
    }

    @Test
    public void selectsCursor_whenHarnessAndCursorEnabled() throws Exception {
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        AgentTaskRouter router = new AgentTaskRouter(props);
        Path ws = Files.createTempDirectory("router-ws2");
        AgentTaskPlan plan = router.plan(AgentTaskType.FRAMEWORK_VALIDATION_V1, ws);
        Assert.assertEquals(plan.providerId(), AgentProviderId.CURSOR);
    }

    @Test
    public void investigationTask_isReadOnlyTools() {
        AgentTaskRouter router = new AgentTaskRouter(new AgentHarnessProperties());
        AgentTaskPlan plan = router.plan(AgentTaskType.EXECUTION_FAILURE_INVESTIGATION_V1, null);
        Assert.assertFalse(plan.policy().allowedTools().contains(AgentToolId.MODIFY_GENERATED_SOURCES));
        Assert.assertTrue(plan.policy().allowedTools().contains(AgentToolId.READ_EXECUTION_LOGS));
    }
}
