package delivery.heal;

import delivery.codegen.ProvenStep;

import java.util.List;

/**
 * Outcome of one heal attempt. Successful tiers are ollama, vision, cursor, or invent.
 */
public record HealResult(
        boolean ok,
        List<ProvenStep> steps,
        String tierUsed,
        String reason
) {
    public static HealResult success(List<ProvenStep> steps, String tierUsed) {
        return new HealResult(true, steps == null ? List.of() : steps, tierUsed, "");
    }

    public static HealResult fail(String reason) {
        return new HealResult(false, List.of(), "", reason == null ? "" : reason);
    }
}
