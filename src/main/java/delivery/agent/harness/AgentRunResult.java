package delivery.agent.harness;

import java.util.List;
import java.util.Map;

/**
 * Provider output envelope. Keel workflows must still run independent Java validation — never trust status alone.
 */
public record AgentRunResult(
        AgentTaskStatus terminalStatus,
        String summary,
        Map<String, Object> payload,
        List<String> modifiedFiles,
        String resultReference
) {
    public AgentRunResult {
        terminalStatus = terminalStatus == null ? AgentTaskStatus.FAILED : terminalStatus;
        summary = summary == null ? "" : summary;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        modifiedFiles = modifiedFiles == null ? List.of() : List.copyOf(modifiedFiles);
        resultReference = resultReference == null ? "" : resultReference;
    }
}
